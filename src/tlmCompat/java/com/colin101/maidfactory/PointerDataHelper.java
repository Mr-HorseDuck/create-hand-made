package com.colin101.maidfactory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PointerDataHelper {

    public static final String NBT_ROOT = "HandMadePointerData";
    public static final String KEY_WORK = "WorkBlock";
    public static final String KEY_INPUT = "InputBlock";
    public static final String KEY_OUTPUT = "OutputBlock";

    // ★ 液体模式（MODE_LIQUID）
    public static final String KEY_LIQUID_INPUT = "LiquidInputBlock";
    public static final String KEY_LIQUID_OUTPUT = "LiquidOutputBlock";
    public static final String KEY_LIQUID_OVERFLOW = "LiquidOverflowBlock";

    private PointerDataHelper() {}

    // ================= MODE_MARK =================

    public static void setWork(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_WORK, level, pos);
    }

    public static void setInput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_INPUT, level, pos);
    }

    public static void setOutput(Entity entity, Level level, @Nullable BlockPos pos) {
        if (pos == null) {
            removeBlock(entity, KEY_OUTPUT);
            return;
        }
        writeBlock(entity, KEY_OUTPUT, level, pos);
    }

    @Nullable
    public static BlockPos getWork(Entity entity, Level level) {
        return readBlock(entity, KEY_WORK, level);
    }

    @Nullable
    public static BlockPos getInput(Entity entity, Level level) {
        return readBlock(entity, KEY_INPUT, level);
    }

    @Nullable
    public static BlockPos getOutput(Entity entity, Level level) {
        return readBlock(entity, KEY_OUTPUT, level);
    }

    // ================= MODE_LIQUID（新增） =================

    public static void setLiquidInput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_INPUT, level, pos);
    }

    public static void setLiquidOutput(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_OUTPUT, level, pos);
    }

    public static void setLiquidOverflow(Entity entity, Level level, BlockPos pos) {
        writeBlock(entity, KEY_LIQUID_OVERFLOW, level, pos);
    }

    @Nullable
    public static BlockPos getLiquidInput(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_INPUT, level);
    }

    @Nullable
    public static BlockPos getLiquidOutput(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_OUTPUT, level);
    }

    @Nullable
    public static BlockPos getLiquidOverflow(Entity entity, Level level) {
        return readBlock(entity, KEY_LIQUID_OVERFLOW, level);
    }

    /** 清除液体模式的三个坐标 */
    public static void clearLiquid(Entity entity) {
        removeBlock(entity, KEY_LIQUID_INPUT);
        removeBlock(entity, KEY_LIQUID_OUTPUT);
        removeBlock(entity, KEY_LIQUID_OVERFLOW);
    }

    /**
     * ★ 只清输出和过剩，保留输入。
     * 用于三标满后再次标记、覆盖输入的场景。
     */
    public static void clearLiquidPartial(Entity entity) {
        removeBlock(entity, KEY_LIQUID_OUTPUT);
        removeBlock(entity, KEY_LIQUID_OVERFLOW);
    }

    // ================= 通用 =================

    /**
     * 维度 ID 的字符串缓存（Level → "minecraft:overworld"）。
     *
     * <p>{@code level.dimension().location()} 本身不分配，但 {@code toString()} 每次都新建
     * 一个字符串。读写标记的路径挂在女仆行为的每次 tryStart 上（每个冷却窗口 1~6 次），
     * 而维度在一个 Level 生命周期内不会变，没必要反复拼。</p>
     *
     * <p>用 WeakHashMap：维度卸载后条目自动消失；外面包一层同步，因为不假设只有服务端线程会读。</p>
     */
    private static final Map<Level, String> DIM_ID_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static String dimIdOf(Level level) {
        String cached = DIM_ID_CACHE.get(level);
        if (cached != null) return cached;

        String id = level.dimension().location().toString();
        DIM_ID_CACHE.put(level, id);
        return id;
    }

    private static void writeBlock(Entity entity, String key, Level level, BlockPos pos) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putString("Dim", dimIdOf(level));
        root.put(key, tag);
        entity.getPersistentData().put(NBT_ROOT, root);
    }

    private static void removeBlock(Entity entity, String key) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        root.remove(key);
        entity.getPersistentData().put(NBT_ROOT, root);
    }

    @Nullable
    private static BlockPos readBlock(Entity entity, String key, Level level) {
        CompoundTag root = entity.getPersistentData().getCompound(NBT_ROOT);
        if (!root.contains(key)) return null;

        CompoundTag tag = root.getCompound(key);

        // ★ 不比较 toString()：那会在每次读取时新分配一个字符串，而这段代码挂在女仆行为
        //   的每次 tryStart 上（每个冷却窗口读 1~6 次）。维度字符串按 Level 缓存，
        //   这里只做一次 map 查找 + 字符串比较，零分配。
        if (!tag.getString("Dim").equals(dimIdOf(level))) return null;

        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    public static void clear(Entity entity) {
        entity.getPersistentData().remove(NBT_ROOT);
    }

    /**
     * 把玩家身上的整块标记复制给女仆。
     *
     * <p>★ 两类标记是**互斥**的（用户拍板）：同一只女仆要么听「加工/部署」的目标，
     * 要么听「液体搬运」的目标，不会同时持有。所以这里是整块覆盖而不是合并——
     * 应用液体标记会清掉她原来的工作方块/输入/输出，反之亦然。
     * 覆盖是刻意的，不是 bug；提示语里已经说明了当前应用的是哪一类。</p>
     *
     * <p>如果将来要改成共存，必须在这里按模式只覆盖各自那几个 key，
     * 并且要一并检查所有读侧（9 个行为类的 tryStart）是否会因为拿到半套数据而误动作。</p>
     */
    public static void copyFrom(Entity source, Entity target) {
        CompoundTag root = source.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) return;

        // 按来源类型裁掉另一类的残留 key：玩家手上可能两套都留着（先标 MARK 再切液体模式），
        // 整块复制会把"上一种模式的旧坐标"也带过去，女仆就会按过期目标干活。
        int mode = source instanceof net.minecraft.world.entity.player.Player p
                ? Mode.getMode(p.getMainHandItem())
                : Mode.MODE_DEFAULT;

        CompoundTag trimmed = root.copy();
        if (mode == Mode.MODE_LIQUID) {
            trimmed.remove(KEY_WORK);
            trimmed.remove(KEY_INPUT);
            trimmed.remove(KEY_OUTPUT);
        } else {
            trimmed.remove(KEY_LIQUID_INPUT);
            trimmed.remove(KEY_LIQUID_OUTPUT);
            trimmed.remove(KEY_LIQUID_OVERFLOW);
        }

        target.getPersistentData().put(NBT_ROOT, trimmed);
    }

    // ==================== 颜色键 ====================

    /**
     * 指杆标记的颜色键。
     * ★ 值必须与 main 的 HighlightBlockPacket 保持一致的 int 编码（0xRRGGBB，无 alpha）。
     */
    public static final class Colors {

        /** 工作方块（MODE_MARK）—— 黄 */
        public static final int WORK = 0xFFFF00;

        /** 输入（MODE_MARK / MODE_LIQUID 共用）—— 绿 */
        public static final int INPUT = 0x00FF00;

        /** 输出（MODE_MARK / MODE_LIQUID 共用）—— 蓝 */
        public static final int OUTPUT = 0x0080FF;

        /** ★ 过剩输出（MODE_LIQUID 专用，独立颜色键避免覆盖 OUTPUT）—— 橙 */
        public static final int OVERFLOW = 0xFF8000;

        private Colors() {}
    }

    // ==================== 指杆模式 NBT ====================

    /**
     * 指杆模式 NBT 读写。
     * ★ 完全属于 tlmCompat，main 不知道这个 NBT 的存在。
     */
    public static final class Mode {

        public static final int MODE_DEFAULT = 0;
        public static final int MODE_MARK = 1;
        /** ★ 液体搬运模式 */
        public static final int MODE_LIQUID = 2;

        /** 模式总数，用于循环 */
        private static final int MODE_COUNT = 3;

        private static final String NBT_MODE = "HandMadePointerMode";

        private Mode() {}

        public static int getMode(net.minecraft.world.item.ItemStack stack) {
            if (stack.isEmpty()) return MODE_DEFAULT;
            return stack.getOrCreateTag().getInt(NBT_MODE);
        }

        public static void setMode(net.minecraft.world.item.ItemStack stack, int mode) {
            if (stack.isEmpty()) return;
            stack.getOrCreateTag().putInt(NBT_MODE, mode);
        }

        public static void cycleMode(net.minecraft.world.item.ItemStack stack) {
            setMode(stack, (getMode(stack) + 1) % MODE_COUNT);
        }

        /** ★ 是否为液体模式 */
        public static boolean isLiquidMode(net.minecraft.world.item.ItemStack stack) {
            return getMode(stack) == MODE_LIQUID;
        }
    }
}
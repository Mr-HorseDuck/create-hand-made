package com.colin101.maidfactory;

import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.item.PressHammerItem;
import com.alben.createhandmade.item.StirringStaffItem;
import com.alben.createhandmade.recipe.ToolType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaidBubbleHelper {

    private static final Map<UUID, Long> LAST_BUBBLE_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SUCCESS_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CONTINUOUS_START = new ConcurrentHashMap<>();

    /** 气泡冷却：30 秒 */
    private static final int BUBBLE_COOLDOWN_TICKS = 600;
    /** 连续判断：两次成功间隔 < 140 tick 视为连续 */
    private static final int CONTINUOUS_GAP_TICKS = 140;
    /** 连续判断：累计 ≥ 1200 tick（1 分钟）触发 continuous */
    private static final int CONTINUOUS_DURATION_TICKS = 1200;

    private MaidBubbleHelper() {}

    public static void tryShow(EntityMaid maid, ToolType tool, ItemStack stack) {
        String toolKey = toolKeyOf(tool);
        if (toolKey == null) return;

        UUID id = ((Entity) maid).getUUID();
        long now = ((Entity) maid).level().getGameTime();

        // 1) 先更新连续状态：每次行为成功都记录，不受气泡冷却影响
        boolean continuous = updateContinuousState(id, now);

        // 2) 再判断 30 秒气泡冷却：冷却中直接返回，不弹
        Long lastBubble = LAST_BUBBLE_TIME.get(id);
        if (lastBubble != null && now - lastBubble < BUBBLE_COOLDOWN_TICKS) {
            return;
        }

        String tier = tierOf(maid);
        String context = contextOf(stack, continuous);
        String langKey = "message.maidfactory.bubble." + toolKey + "." + tier + "." + context + "."
                + (1 + (int) (Math.random() * 2));

        LAST_BUBBLE_TIME.put(id, now);
        maid.getChatBubbleManager().addTextChatBubble(langKey);

        // 冷却早就过了的条目对判定已无影响，可以清
        sweepIfNeeded(LAST_BUBBLE_TIME, now, BUBBLE_COOLDOWN_TICKS * 2);
    }

    public static void tryShowFirstUse(EntityMaid maid, ToolType tool, ItemStack stack) {
        String toolKey = toolKeyOf(tool);
        if (toolKey == null) return;

        String nbtKey = "maidfactory_used_" + toolKey;
        if (((Entity) maid).getPersistentData().getBoolean(nbtKey)) return;
        ((Entity) maid).getPersistentData().putBoolean(nbtKey, true);

        UUID id = ((Entity) maid).getUUID();
        long now = ((Entity) maid).level().getGameTime();
        LAST_BUBBLE_TIME.put(id, now);   // 占住 30 秒冷却，防止紧接着的 tryShow 覆盖 first
        sweepIfNeeded(LAST_BUBBLE_TIME, now, BUBBLE_COOLDOWN_TICKS * 2);

        maid.getChatBubbleManager().addTextChatBubble(
                "message.maidfactory.bubble." + toolKey + ".first");
    }

    public static void clear(UUID id) {
        LAST_BUBBLE_TIME.remove(id);
        LAST_SUCCESS_TIME.remove(id);
        CONTINUOUS_START.remove(id);
    }

    /**
     * 只清「连续加工」的判定状态，保留 {@code LAST_BUBBLE_TIME}。
     *
     * <p>女仆跨区块 / 换维度时用：清掉冷却时间会让气泡反复重弹，只清连续起点才对。</p>
     */
    public static void clearContinuousState(UUID id) {
        CONTINUOUS_START.remove(id);
        LAST_SUCCESS_TIME.remove(id);
    }

    // ==================== 过期清理 ====================
    // ★ 这三张表都以女仆 UUID 为键，原来只在「女仆死亡」时清理。而女仆被删除、区块永久卸载、
    //   换存档等情况下死亡事件未必触发，长期运行的服务器上条目只增不减（每个约几十字节）。
    //   量级很小，但没理由让它无限长——表长到阈值时顺手扫一遍，把早已过期的条目去掉。

    /** 表超过这个条数就做一次过期清理。 */
    private static final int SWEEP_THRESHOLD = 256;

    /**
     * 清掉「早就不可能再影响判定」的条目。
     *
     * @param keepTicks 超过这个 tick 数的条目视为过期（各表的有效期语义不同，由调用方给）
     */
    private static void sweepIfNeeded(Map<UUID, Long> map, long now, int keepTicks) {
        if (map.size() < SWEEP_THRESHOLD) return;
        map.entrySet().removeIf(entry -> now - entry.getValue() > keepTicks);
    }

    // ==================== 连续判断 ====================

    private static boolean updateContinuousState(UUID id, long now) {
        Long last = LAST_SUCCESS_TIME.get(id);
        LAST_SUCCESS_TIME.put(id, now);

        // 连续判定的两张表：超过「连续间隔」的旧值已经没有意义
        sweepIfNeeded(LAST_SUCCESS_TIME, now, CONTINUOUS_GAP_TICKS * 4);
        sweepIfNeeded(CONTINUOUS_START, now, CONTINUOUS_DURATION_TICKS * 2);

        if (last == null || now - last >= CONTINUOUS_GAP_TICKS) {
            CONTINUOUS_START.remove(id);
            return false;
        }

        Long start = CONTINUOUS_START.get(id);
        if (start == null) {
            CONTINUOUS_START.put(id, last);
            return false;
        }
        return now - start >= CONTINUOUS_DURATION_TICKS;
    }

    // ==================== 分档 ====================

    private static String tierOf(EntityMaid maid) {
        int level = maid.getFavorabilityManager().getLevel();
        if (level <= 1) return "low";
        if (level == 2) return "mid";
        return "high";
    }

    private static String contextOf(ItemStack stack, boolean continuous) {
        if (stack.isDamageableItem()) {
            float remaining = 1.0F - stack.getDamageValue() / (float) stack.getMaxDamage();
            if (remaining <= 0.2F) return "durability";
        }
        if (continuous) return "continuous";
        return "normal";
    }

    // ==================== 映射 ====================

    /** ItemStack → ToolType；非手搓工具返回 null。供装备变更事件使用。 */
    @Nullable
    public static ToolType toolTypeOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var item = stack.getItem();
        if (item instanceof HandSawItem) return ToolType.HAND_SAW;
        if (item instanceof MortarItem) return ToolType.MORTAR;
        if (item instanceof CrusherMortarItem) return ToolType.CRUSHER_MORTAR;
        if (item instanceof PressHammerItem) return ToolType.PRESS_HAMMER_DEPOT;
        if (item instanceof InfusionGunItem) return ToolType.INFUSION_GUN;
        if (item instanceof StirringStaffItem) return ToolType.STIRRING_STAFF;
        if (item instanceof BellowsItem) return ToolType.BELLOWS;
        if (item instanceof PointerItem) return ToolType.POINTER;
        return null;
    }

    private static String toolKeyOf(ToolType tool) {
        return switch (tool) {
            case HAND_SAW -> "hand_saw";
            case MORTAR -> "mortar";
            case CRUSHER_MORTAR -> "crusher_mortar";
            case PRESS_HAMMER_DEPOT, PRESS_HAMMER_BASIN -> "press_hammer";
            case INFUSION_GUN -> "infusion_gun";
            case STIRRING_STAFF -> "stirring_staff";
            case BELLOWS -> "bellows";
            case POINTER -> "pointer";
            default -> null;
        };
    }
}
package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.network.FluidParticlesPacket;
import com.alben.createhandmade.recipe.HandMadeFillingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.fluids.transfer.FillingInput;
import com.zurrtum.create.content.fluids.transfer.FillingRecipe;
import com.zurrtum.create.content.fluids.transfer.GenericItemFilling;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.foundation.fluid.FluidHelper;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.particle.FluidParticleData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 灌注枪：抽液 / 注液 / 存液 / 放流体方块。
 *
 * <p>26.1 的 Create Fly 已把流体能力统一成 {@link FluidInventory}，
 * 通过 {@link FluidHelper#getFluidInventory(Level, BlockPos, Direction)} 获取；
 * NeoForge 的 {@code Capabilities.FluidHandler.BLOCK} 不再存在。</p>
 */
public class InfusionGunItem extends Item {

    // ================== 交互参数（方便调优） ==================

    // 长按阈值 / 抽取间隔已移到 ServerConfig（tools.infusion_gun.long_press_threshold /
    // extract_interval_ticks），运行时读 —— 见 onUseTick() 与 releaseUsing()。

    /** 容器抽取（潜行长按）：单次抽取量（mB → droplets）——「量」，不进 config */
    private static final int EXTRACT_RATE = 25 * InfusionGunContents.DROPLETS_PER_MB;

    /** 音效响度 */
    private static final float FILL_SOUND_VOLUME = 0.7f;

    /**
     * 流体方块：一整桶的量（droplets）——「量」，不进 config。
     *
     * <p>Create Fly 的 {@code FluidStack} 以 droplets 计数（1 桶 = 81000），
     * 不是 NeoForge 版的 1000 mB。见 {@link InfusionGunContents#DROPLETS_PER_MB}。</p>
     */
    private static final int FLUID_BLOCK_AMOUNT = 1000 * InfusionGunContents.DROPLETS_PER_MB;

    // ================== 玩家状态 ==================

    private static final Map<UUID, PressInfo> PRESSING = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_EXTRACT = new HashMap<>();
    private static final Map<UUID, Boolean> EXTRACT_DAMAGED = new HashMap<>();

    /**
     * 本次长按是否已经触发过"流体方块一次性吸取"。
     * 用于配合"到达长按门槛"的判定，保证一次长按只吸一格。
     */
    private static final Set<UUID> PICKUP_TRIGGERED = new HashSet<>();

    /**
     * 本次长按是否已经触发过"不潜行快速抽满容器"。
     * 与 {@link #PICKUP_TRIGGERED} 同理，保证一次长按只抽一次。
     */
    private static final Set<UUID> EXTRACT_TRIGGERED = new HashSet<>();

    /**
     * 玩家在 UseBlockCallback 时保存的交互上下文。
     *
     * @param pos   点击的方块位置
     * @param mode  长按要走的分支：容器 / 流体方块
     * @param face  点击的面（短按放流体方块时需要）
     */
    public record PressInfo(BlockPos pos, InteractionMode mode, Direction face) {
        public enum InteractionMode { CONTAINER, FLUID_BLOCK }
    }

    public InfusionGunItem(Properties properties) {
        super(properties);
    }

    /**
     * 灌注枪诊断日志开关：{@code -Dcreate_hand_made.debugInfusion=true}。
     *
     * <p>打印每一次抽取 / 存入 / 放置前后的真实库存内容。工作盆的
     * {@code BasinFluidHandler} 有 4 个槽位：0/1 = 输出罐，2/3 = 输入罐 ——
     * 用它可以直接看出「枪里满了但盆没少」到底少的是哪个罐。</p>
     */
    private static final boolean DEBUG_INFUSION = Boolean.getBoolean("create_hand_made.debugInfusion");

    private static void debug(String message) {
        System.out.println("[CHM-GUN] " + message);
    }

    private static String describeInventory(FluidInventory inventory) {
        StringBuilder sb = new StringBuilder(inventory.getClass().getSimpleName())
                .append("{size=").append(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            FluidStack stack = inventory.getStack(i);
            sb.append(", ").append(i).append('=')
                    .append(stack.isEmpty() ? "empty" : (stack.getFluid() + "x" + stack.getAmount()));
        }
        return sb.append('}').toString();
    }

    private static String describeGun(ItemStack gun) {
        InfusionGunContents contents = getContents(gun);
        return contents.isEmpty() ? "empty" : (contents.fluid().getFluid() + "x" + contents.amount());
    }

    // ================== 拦截右键：只做拦截，不记录状态 ==================
    //
    // Fabric 用 UseBlockCallback 取代 NeoForge 的 PlayerInteractEvent.RightClickBlock。
    // 回调里做一次 SOURCE_ONLY 射线，命中可交互目标时返回 SUCCESS 抑制方块自身交互；
    // 真正的状态记录与长按开始交给 use()。

    public static InteractionResult interceptRightClick(Player player, Level level, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof InfusionGunItem)) {
            return InteractionResult.PASS;
        }

        // ★ 主动做一次 SOURCE_ONLY 射线：如果玩家真正瞄的是流体源，就用它的位置。
        //   原版传给回调的 hit 来自 Fluid.NONE 射线，会"穿透"水面命中水后面的方块。
        BlockPos pos;
        BlockHitResult fluidHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (fluidHit.getType() == HitResult.Type.BLOCK && isFluidSource(level, fluidHit.getBlockPos())) {
            pos = fluidHit.getBlockPos();
        } else {
            pos = hit.getBlockPos();
        }

        if (!canInteractAt(level, pos, gun)) {
            return InteractionResult.PASS;
        }

        // Fabric 的 UseBlockCallback 返回非 PASS 会短路整个 useItemOn，
        // 物品自己的 use() 不会再被调用 ⇒ 这里手动进入长按（两端各执行一次）。
        return gun.use(level, player, hand);
    }

    // ================== use：进入长按（并记录交互上下文） ==================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        if (!canInteractAt(level, hit.getBlockPos(), gun)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            // ★ 长按分支判定：潜行时优先流体方块；不潜行时优先容器
            PressInfo.InteractionMode mode;
            if (player.isShiftKeyDown()) {
                mode = isFluidSource(level, hit.getBlockPos())
                        ? PressInfo.InteractionMode.FLUID_BLOCK
                        : PressInfo.InteractionMode.CONTAINER;
            } else {
                FluidInventory handler = FluidHelper.getFluidInventory(level, hit.getBlockPos(), null);
                if (handler != null) {
                    mode = PressInfo.InteractionMode.CONTAINER;
                } else if (isFluidSource(level, hit.getBlockPos())) {
                    mode = PressInfo.InteractionMode.FLUID_BLOCK;
                } else {
                    mode = PressInfo.InteractionMode.CONTAINER;   // 兜底
                }
            }

            PRESSING.put(player.getUUID(), new PressInfo(
                    hit.getBlockPos(), mode, hit.getDirection()));
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    // ================== onUseTick：长按期间的处理 ==================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) return;

        PressInfo info = PRESSING.get(player.getUUID());
        if (info == null) return;

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (usedTicks < 3) return;

        // 长按阈值 / 抽取节奏：SERVER 配置，运行时读
        int longPressThreshold = ServerConfig.INSTANCE.infusionGunLongPressThreshold.get();
        int extractInterval = Math.max(1, ServerConfig.INSTANCE.infusionGunExtractIntervalTicks.get());

        boolean sneaking = player.isShiftKeyDown();

        if (info.mode() == PressInfo.InteractionMode.CONTAINER) {
            if (sneaking) {
                // 潜行：每 extractInterval tick 抽 25mB（慢速精确抽）
                if (usedTicks % extractInterval != 0) return;
                tryExtractTick(level, info.pos(), player, stack);
            } else {
                // 不潜行：达到 longPressThreshold 后每 extractInterval tick 尝试一次抽满；
                // 成功才置 flag 停止，失败则下个周期继续重试（例如枪内剩余空间不足时）
                if (usedTicks >= longPressThreshold
                        && (usedTicks - longPressThreshold) % extractInterval == 0
                        && !EXTRACT_TRIGGERED.contains(player.getUUID())) {
                    if (tryExtractMax(level, info.pos(), player, stack)) {
                        EXTRACT_TRIGGERED.add(player.getUUID());
                    }
                }
            }
        } else {  // FLUID_BLOCK
            FluidStack source = getFluidFromSource(level, info.pos());

            if (sneaking) {
                // 潜行：音效 + 粒子按抽取节奏播
                if (!source.isEmpty() && usedTicks % extractInterval == 0) {
                    level.playSound(null, player.blockPosition(),
                            FluidHelper.getFillSound(source),
                            SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f);
                }
                if (!source.isEmpty()) {
                    spawnPickupProgressParticles(level, player, info.pos(), source);
                }
                // 潜行也改成"到长按门槛即可吸走整格"，
                // 与不潜行分支同构：从门槛起每 extractInterval 重试一次，成功才置 flag。
                if (usedTicks >= longPressThreshold
                        && (usedTicks - longPressThreshold) % extractInterval == 0
                        && !source.isEmpty()
                        && !PICKUP_TRIGGERED.contains(player.getUUID())) {
                    if (tryPickupFluidBlock(level, info.pos(), player, stack)) {
                        PICKUP_TRIGGERED.add(player.getUUID());
                    }
                }
            } else {
                // 不潜行：达到 longPressThreshold 后每 extractInterval tick 尝试一次立即吸；
                // 成功才置 flag + 播音效/粒子，失败则下个周期继续重试
                if (usedTicks >= longPressThreshold
                        && (usedTicks - longPressThreshold) % extractInterval == 0
                        && !source.isEmpty()
                        && !PICKUP_TRIGGERED.contains(player.getUUID())) {
                    if (tryPickupFluidBlock(level, info.pos(), player, stack)) {
                        PICKUP_TRIGGERED.add(player.getUUID());
                        level.playSound(null, player.blockPosition(),
                                FluidHelper.getFillSound(source),
                                SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f);
                        // 一批粒子（spawnPickupProgressParticles 每次 3 个，调 3 次 = 9 个）
                        for (int i = 0; i < 3; i++) {
                            spawnPickupProgressParticles(level, player, info.pos(), source);
                        }
                    }
                }
            }
        }
    }

    // ================== releaseUsing：松手 ==================

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        if (level.isClientSide()) return false;

        PENDING_EXTRACT.remove(player.getUUID());
        EXTRACT_DAMAGED.remove(player.getUUID());
        PICKUP_TRIGGERED.remove(player.getUUID());
        EXTRACT_TRIGGERED.remove(player.getUUID());

        PressInfo info = PRESSING.remove(player.getUUID());
        if (info == null) return false;

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        // 长按门槛读 SERVER 配置（运行时读）
        if (usedTicks >= ServerConfig.INSTANCE.infusionGunLongPressThreshold.get()) return false;   // 长按已由 onUseTick 处理

        // ★ 目标守卫：这里只保留「点击的不是容器」。
        //   - 目标是容器（有 FluidInventory）→ 不放（否则可能洒到容器旁边）
        //   - 其余（空气、石头、流体等）→ 潜行与不潜行都放，但优先级低于注入 / 存容器
        boolean isContainer = FluidHelper.getFluidInventory(level, info.pos(), null) != null;
        boolean canPlaceFluid = !isContainer;

        // ★ 短按：按优先级尝试。注入物品 + 存容器是一组，注入优先。
        if (player.isShiftKeyDown()) {
            // 潜行：放流体方块 → 注入 → 存容器
            if (canPlaceFluid && tryPlaceFluidBlock(level, info.pos(), info.face(), player, stack)) {
                if (DEBUG_INFUSION) debug("shortPress(sneak) placed fluid at " + info.pos());
                return true;
            }
            if (tryInjectItems(level, info.pos(), player, stack)) {
                if (DEBUG_INFUSION) debug("shortPress(sneak) injected items at " + info.pos());
                return true;
            }
            if (tryStoreFluid(level, info.pos(), player, stack)) {
                if (DEBUG_INFUSION) debug("shortPress(sneak) stored fluid at " + info.pos());
                return true;
            }
            return false;
        }
        // 不潜行：注入 → 存容器 → 放流体方块
        if (tryInjectItems(level, info.pos(), player, stack)) {
            if (DEBUG_INFUSION) debug("shortPress injected items at " + info.pos());
            return true;
        }
        if (tryStoreFluid(level, info.pos(), player, stack)) {
            if (DEBUG_INFUSION) debug("shortPress stored fluid at " + info.pos());
            return true;
        }
        if (canPlaceFluid) {
            boolean placed = tryPlaceFluidBlock(level, info.pos(), info.face(), player, stack);
            if (DEBUG_INFUSION) debug("shortPress place=" + placed + " at " + info.pos());
            return placed;
        }
        return false;
    }

    // ================== 抽取容器（累计判定，兼容整桶容器） ==================

    private static void tryExtractTick(Level level, BlockPos pos, Player player, ItemStack gun) {
        FluidInventory handler = FluidHelper.getFluidInventory(level, pos, null);
        if (handler == null) return;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() <= 0) return;

        int pending = Math.min(
                PENDING_EXTRACT.getOrDefault(player.getUUID(), 0) + EXTRACT_RATE,
                InfusionGunContents.CAPACITY);

        FluidStack source = FluidStack.EMPTY;
        for (int i = 0; i < handler.size(); i++) {
            FluidStack inTank = handler.getStack(i);
            if (inTank.isEmpty()) continue;
            if (!contents.isEmpty() && !FluidStack.areFluidsAndComponentsEqual(contents.fluid(), inTank)) {
                continue;
            }
            source = inTank;
            break;
        }

        if (source.isEmpty()) {
            PENDING_EXTRACT.remove(player.getUUID());
            return;
        }

        int want = Math.min(Math.min(pending, contents.remaining()), source.getAmount());
        if (want <= 0) return;

        if (DEBUG_INFUSION) {
            debug("extractTick pos=" + pos + " want=" + want + " pending=" + pending
                    + " gun=" + describeGun(gun) + " inv=" + describeInventory(handler));
        }

        // ★ 音效放在抽取之前 —— 只要源里有流体，就按周期给出"正在抽"的反馈。
        //   炼药锅这类按整桶出入的容器，抽 25mB 会返回 0；若把音效放在抽取之后，
        //   就会被下面 actual <= 0 的提前 return 吃掉，玩家长按听不到任何声音，
        //   会误以为工具坏了。
        level.playSound(null, pos, FluidHelper.getFillSound(source),
                SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f + level.getRandom().nextFloat() * 0.2f);

        int drained = handler.extract(source.copyWithAmount(want), null);
        if (drained <= 0) {
            // 容器抽不出（如整桶出入的容器），保留 pending 等下次
            PENDING_EXTRACT.put(player.getUUID(), pending);
            if (DEBUG_INFUSION) {
                debug("extractTick drained=0 invAfter=" + describeInventory(handler));
            }
            return;
        }

        PENDING_EXTRACT.put(player.getUUID(), Math.max(0, pending - drained));
        setContents(gun, contents.withFill(source.copyWithAmount(drained)));

        if (DEBUG_INFUSION) {
            debug("extractTick drained=" + drained + " gunAfter=" + describeGun(gun)
                    + " invAfter=" + describeInventory(handler));
        }

        if (!EXTRACT_DAMAGED.getOrDefault(player.getUUID(), false)) {
            damageGun(gun, player);
            EXTRACT_DAMAGED.put(player.getUUID(), true);
        }
    }

    /**
     * 一次性抽满容器（不潜行长按用）。
     *
     * <p>与 {@link #tryExtractTick} 的区别：不做"每 tick 25mB"的累计，
     * 直接按 {@code min(枪内剩余容量, 源剩余量)} 一次抽完，所以是瞬间完成。</p>
     *
     * @return 真的抽到流体并写入枪内时为 true
     */
    private static boolean tryExtractMax(Level level, BlockPos pos, Player player, ItemStack gun) {
        FluidInventory handler = FluidHelper.getFluidInventory(level, pos, null);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() <= 0) return false;

        FluidStack source = FluidStack.EMPTY;
        for (int i = 0; i < handler.size(); i++) {
            FluidStack inTank = handler.getStack(i);
            if (inTank.isEmpty()) continue;
            if (!contents.isEmpty()
                    && !FluidStack.areFluidsAndComponentsEqual(contents.fluid(), inTank)) {
                continue;
            }
            source = inTank;
            break;
        }
        if (source.isEmpty()) return false;

        int want = Math.min(contents.remaining(), source.getAmount());
        if (want <= 0) return false;

        if (DEBUG_INFUSION) {
            debug("extractMax pos=" + pos + " want=" + want + " gun=" + describeGun(gun)
                    + " inv=" + describeInventory(handler));
        }

        int drained = handler.extract(source.copyWithAmount(want), null);
        if (drained <= 0) return false;

        FluidStack actual = source.copyWithAmount(drained);
        setContents(gun, contents.withFill(actual));
        damageGun(gun, player);

        if (DEBUG_INFUSION) {
            debug("extractMax drained=" + drained + " gunAfter=" + describeGun(gun)
                    + " invAfter=" + describeInventory(handler));
        }

        level.playSound(null, pos, FluidHelper.getFillSound(actual),
                SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f + level.getRandom().nextFloat() * 0.2f);

        return true;
    }

    // ================== 吸取流体方块 ==================

    /**
     * 从流体源方块吸取 1000mB 到枪内。
     * 严格判定：枪内空或同种、剩余空间 ≥1000mB、目标必须是源方块。
     */
    private static boolean tryPickupFluidBlock(Level level, BlockPos pos, Player player, ItemStack gun) {
        FluidStack source = getFluidFromSource(level, pos);
        if (source.isEmpty()) return false;

        InfusionGunContents contents = getContents(gun);

        // 严格判定：枪内非空且与源不同 → 拒绝
        if (!contents.isEmpty() && !FluidStack.areFluidsAndComponentsEqual(contents.fluid(), source)) {
            return false;
        }
        if (contents.remaining() < FLUID_BLOCK_AMOUNT) return false;

        // 源方块消失
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        level.playSound(null, pos, FluidHelper.getFillSound(source),
                SoundSource.PLAYERS, 1.0f, 1.0f);

        setContents(gun, contents.withFill(source));
        damageGun(gun, player);
        return true;
    }

    /**
     * 从方块位置读出一个"源流体"（1000mB）。
     * 只处理源方块 + 有方块形式的流体；否则返回空。
     */
    private static FluidStack getFluidFromSource(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty()) return FluidStack.EMPTY;
        if (!state.isSource()) return FluidStack.EMPTY;

        Fluid fluid = state.getType();
        if (fluid == Fluids.EMPTY) return FluidStack.EMPTY;

        // ★ 用「有无方块形式」判断，而不是 fluid.getBucket()。
        //   Create 的蜂蜜/巧克力虽然有各自的桶物品，fluid.getBucket() 却返回原版 AIR，
        //   用桶判断会把它们误排除掉。这里的判据与 tryPlaceFluidBlock 保持一致。
        if (!FluidHelper.hasBlockState(fluid)) return FluidStack.EMPTY;

        return new FluidStack(fluid, FLUID_BLOCK_AMOUNT);
    }

    private static boolean isFluidSource(Level level, BlockPos pos) {
        return !getFluidFromSource(level, pos).isEmpty();
    }

    // ================== 放置流体方块 ==================

    /**
     * 本次"放流体源"落在哪一格。
     *
     * <ul>
     *   <li>点击位置<b>本身就是流体</b>（不论同种 / 异种）→ <b>原地覆盖它</b>。</li>
     *   <li>其余（空气、石头等）→ 点击面的相邻格。</li>
     * </ul>
     */
    private static BlockPos placementTarget(Level level, BlockPos clickedPos, Direction face) {
        if (!level.getFluidState(clickedPos).isEmpty()) return clickedPos;
        return clickedPos.relative(face);
    }

    /**
     * 把枪内 1000mB 流体放到目标格（目标格的选取见 {@link #placementTarget}）。
     * 严格判定：枪内 ≥1000mB、目标格可被顶掉、流体有方块形式。
     */
    private static boolean tryPlaceFluidBlock(Level level, BlockPos clickedPos, Direction face,
                                              Player player, ItemStack gun) {
        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;
        if (contents.amount() < FLUID_BLOCK_AMOUNT) return false;

        BlockPos targetPos = placementTarget(level, clickedPos, face);
        BlockState targetState = level.getBlockState(targetPos);
        if (!canReplaceWithSource(targetState)) return false;

        Fluid fluid = contents.fluid().getFluid();
        if (fluid == Fluids.EMPTY) return false;

        // 检查流体是否有方块形式
        if (!FluidHelper.hasBlockState(fluid)) return false;

        BlockState fluidBlockState = fluid.defaultFluidState().createLegacyBlock();

        // ★ "算不算放成功"与原版水桶逐字对齐：
        //   - setBlock 真的改了世界（空气→水、流动→源、异种源被顶掉）→ 成功，扣流体；
        //   - 没改世界、但原来那格本就是流体源（同种源上"白放"）→ 仍算成功，照扣流体 + 音效；
        //   - 没改世界且原来不是源（例如超出建筑高度）→ 不算成功，不扣流体。
        boolean changed = level.setBlock(targetPos, fluidBlockState, 11);
        if (!changed && !targetState.getFluidState().isSource()) return false;

        level.playSound(null, targetPos, FluidHelper.getEmptySound(contents.fluid()),
                SoundSource.PLAYERS, 1.0f, 1.0f + level.getRandom().nextFloat() * 0.2f);

        setContents(gun, contents.withDrain(FLUID_BLOCK_AMOUNT));
        damageGun(gun, player);
        return true;
    }

    /**
     * 水桶式的「这个格子能不能被本枪的流体顶掉」判据。
     * 空气、花草、流体（流动的和源都算）→ 可顶；石头、台阶等 → 拒绝。
     */
    private static boolean canReplaceWithSource(BlockState state) {
        return state.canBeReplaced();
    }

    /**
     * 在流体源方块上方生成"滴落"粒子。
     */
    private static void spawnPickupProgressParticles(Level level, Player player,
                                                     BlockPos sourcePos, FluidStack fluid) {
        if (fluid.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ★ 用流体实际高度精确定位：基准必须是方块底面中心
        double fluidHeight = level.getFluidState(sourcePos).getOwnHeight();
        Vec3 center = Vec3.atBottomCenterOf(sourcePos).add(0, fluidHeight + 0.1, 0);

        ParticleOptions particle = FluidParticleData.particle(fluid.getFluid(), fluid.getComponentChanges());
        RandomSource random = level.getRandom();

        for (int i = 0; i < 3; i++) {
            double ox = (random.nextDouble() - 0.5) * 0.7;
            double oz = (random.nextDouble() - 0.5) * 0.7;
            Vec3 spawnPos = center.add(ox, 0, oz);

            double vx = (random.nextDouble() - 0.5) * 0.04;
            double vy = -0.08 - random.nextDouble() * 0.06;
            double vz = (random.nextDouble() - 0.5) * 0.04;

            serverLevel.sendParticles(particle,
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    1,
                    vx, vy, vz,
                    0.0);
        }
    }

    // ================== 注入物品 ==================

    private static boolean tryInjectItems(Level level, BlockPos pos, Player player, ItemStack gun) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            // 1. FillingRecipe（Create 的 + L3 独占的，见 findFillingRecipe）
            RecipeHolder<? extends Recipe<FillingInput>> recipe =
                    findFillingRecipe(level, stack, contents.fluid());
            if (recipe != null) {
                int required = requiredFluidOf(recipe.value()).amount();
                if (contents.fluid().getAmount() < required) return TransportedResult.doNothing();

                List<ItemStack> results = assembleFilling(recipe.value(), stack, contents.fluid());

                success[0] = true;
                setContents(gun, contents.withDrain(required));
                broadcastFluidParticles(level, pos, contents.fluid());

                return finishInject(item, stack, results);
            }

            // 2. GenericItemFilling
            if (GenericItemFilling.canItemBeFilled(level, stack)) {
                int required = GenericItemFilling.getRequiredAmountForItem(level, stack, contents.fluid());
                if (required <= 0) return TransportedResult.doNothing();
                if (contents.fluid().getAmount() < required) return TransportedResult.doNothing();

                ItemStack simulatedResult = GenericItemFilling.fillItem(
                        level, required, stack.copy(), contents.fluid().copy());
                if (simulatedResult.isEmpty()) return TransportedResult.doNothing();

                success[0] = true;
                setContents(gun, contents.withDrain(required));
                broadcastFluidParticles(level, pos, contents.fluid());

                List<ItemStack> results = new ArrayList<>();
                results.add(simulatedResult);
                return finishInject(item, stack, results);
            }

            return TransportedResult.doNothing();
        });

        if (success[0]) {
            damageGun(gun, player);

            level.playSound(null, pos, FluidHelper.getEmptySound(contents.fluid()),
                    SoundSource.PLAYERS, 0.8f, 1.0f + level.getRandom().nextFloat() * 0.2f);
        }
        return success[0];
    }

    private static TransportedResult finishInject(TransportedItemStack item, ItemStack stack,
                                                  List<ItemStack> results) {
        ItemStack left = stack.copy();
        left.shrink(1);

        List<TransportedItemStack> outputs = new ArrayList<>();
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            TransportedItemStack copy = item.copy();
            copy.stack = result;
            outputs.add(copy);
        }

        if (outputs.isEmpty()) {
            if (left.isEmpty()) return TransportedResult.removeItem();
            TransportedItemStack leftStack = item.copy();
            leftStack.stack = left;
            return TransportedResult.convertTo(leftStack);
        }

        if (left.isEmpty()) {
            return TransportedResult.convertTo(outputs);
        } else {
            TransportedItemStack leftStack = item.copy();
            leftStack.stack = left;
            return TransportedResult.convertToAndLeaveHeld(outputs, leftStack);
        }
    }

    // ================== 存入流体容器 ==================

    private static boolean tryStoreFluid(Level level, BlockPos pos, Player player, ItemStack gun) {
        FluidInventory handler = FluidHelper.getFluidInventory(level, pos, null);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;

        FluidStack fluid = contents.fluid();
        if (DEBUG_INFUSION) {
            debug("store pos=" + pos + " gun=" + describeGun(gun) + " inv=" + describeInventory(handler));
        }
        int actual = handler.insert(fluid.copy(), null);
        if (actual <= 0) return false;

        setContents(gun, contents.withDrain(actual));
        damageGun(gun, player);

        if (DEBUG_INFUSION) {
            debug("store inserted=" + actual + " gunAfter=" + describeGun(gun)
                    + " invAfter=" + describeInventory(handler));
        }

        level.playSound(null, pos, FluidHelper.getEmptySound(fluid),
                SoundSource.PLAYERS, 1f, 1.0f + level.getRandom().nextFloat() * 0.2f);
        return true;
    }

    // ================== 耐久 ==================

    private static void damageGun(ItemStack gun, Player player) {
        gun.hurtAndBreak(1, player, player.getUsedItemHand());
    }

    // ================== 判定：能否交互 ==================

    private static boolean canInteractAt(Level level, BlockPos pos, ItemStack gun) {
        InfusionGunContents contents = getContents(gun);

        // 流体源方块：总是"可交互"（吸/放都由后续分支处理）
        if (isFluidSource(level, pos)) return true;

        // 容器：有液体可吸 或 枪有液体可存
        FluidInventory fluidHandler = FluidHelper.getFluidInventory(level, pos, null);
        if (fluidHandler != null) {
            if (!contents.isEmpty()) return true;
            for (int i = 0; i < fluidHandler.size(); i++) {
                if (!fluidHandler.getStack(i).isEmpty()) return true;
            }
        }

        // 注入物品
        if (!contents.isEmpty() && canInjectItemsAt(level, pos, contents)) {
            return true;
        }

        // 放流体方块：枪内 ≥1000mB 且目标格可被顶掉
        if (!contents.isEmpty() && contents.amount() >= FLUID_BLOCK_AMOUNT) {
            for (Direction dir : Direction.values()) {
                BlockPos target = pos.relative(dir);
                if (canReplaceWithSource(level.getBlockState(target))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean canInjectItemsAt(Level level, BlockPos pos, InfusionGunContents contents) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] found = {false};
        handler.handleProcessingOnAllItems(item -> {
            if (found[0]) return TransportedResult.doNothing();
            if (item.stack.isEmpty()) return TransportedResult.doNothing();

            if (findFillingRecipe(level, item.stack, contents.fluid()) != null) {
                found[0] = true;
                return TransportedResult.doNothing();
            }

            if (GenericItemFilling.canItemBeFilled(level, item.stack)) {
                int required = GenericItemFilling.getRequiredAmountForItem(level, item.stack, contents.fluid());
                if (required > 0 && contents.fluid().getAmount() >= required) {
                    found[0] = true;
                }
            }
            return TransportedResult.doNothing();
        });
        return found[0];
    }

    // ================== 配方查找 ==================

    /**
     * 查找一条能加工 {@code target} 的注液配方。
     *
     * <p>Create Fly 在加载期已把序列组装展开成普通 FILLING 配方，
     * 所以这里不需要（也没有）{@code SequencedAssemblyRecipe.getRecipe} 特例。</p>
     */
    @Nullable
    private static RecipeHolder<? extends Recipe<FillingInput>> findFillingRecipe(
            Level level, ItemStack target, FluidStack fluid) {
        FillingInput input = new FillingInput(target, fluid);

        // 候选集由统一配方池提供；匹配与流体判定留在本类。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.INFUSION_GUN, level)) {
            // L3 独占配方（注液家族）：只认归属灌注枪的那些。
            if (holder.value() instanceof HandMadeFillingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.INFUSION_GUN) continue;
                if (!exclusive.matches(input, level)) continue;
                return new RecipeHolder<>(holder.id(), exclusive);
            }

            if (!(holder.value() instanceof FillingRecipe fr)) continue;
            if (!fr.matches(input, level)) continue;
            return new RecipeHolder<>(holder.id(), fr);
        }

        return null;
    }

    /** 取「注液类」配方要求的流体。 */
    private static FluidIngredient requiredFluidOf(Recipe<FillingInput> recipe) {
        if (recipe instanceof FillingRecipe filling) {
            return filling.fluidIngredient();
        }
        if (recipe instanceof HandMadeFillingRecipe exclusive) {
            return exclusive.fluidIngredient();
        }
        throw new IllegalStateException("Not a filling recipe: " + recipe);
    }

    /** 产出注液结果。 */
    private static List<ItemStack> assembleFilling(Recipe<FillingInput> recipe, ItemStack target, FluidStack fluid) {
        FillingInput input = new FillingInput(target.copyWithCount(1), fluid);
        ItemStack result = recipe.assemble(input);
        return result.isEmpty() ? List.of() : List.of(result);
    }

    // ================== 数据组件 ==================

    public static InfusionGunContents getContents(ItemStack gun) {
        InfusionGunContents contents = gun.get(ModDataComponents.INFUSION_GUN_CONTENTS);
        if (contents == null) {
            contents = new InfusionGunContents(FluidStack.EMPTY);
        }
        return contents;
    }

    public static void setContents(ItemStack gun, InfusionGunContents contents) {
        if (contents.isEmpty()) {
            gun.remove(ModDataComponents.INFUSION_GUN_CONTENTS);
        } else {
            gun.set(ModDataComponents.INFUSION_GUN_CONTENTS, contents);
        }
    }

    // ================== 玩家下线清理 ==================

    /** 由 Fabric 的断线事件调用（替代 NeoForge 的 PlayerLoggedOutEvent）。 */
    public static void clearPlayer(UUID uuid) {
        PRESSING.remove(uuid);
        PENDING_EXTRACT.remove(uuid);
        EXTRACT_DAMAGED.remove(uuid);
        PICKUP_TRIGGERED.remove(uuid);
        EXTRACT_TRIGGERED.remove(uuid);
    }

    // ================== Tooltip ==================

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        InfusionGunContents contents = getContents(stack);

        if (contents.isEmpty()) {
            tooltip.accept(Component.translatable("item.create_hand_made.infusion_gun.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            FluidStack fluid = contents.fluid();
            // FluidStack 里是 droplets（1 mB = 81），提示按 mB 显示，与原版一致。
            tooltip.accept(Component.translatable("item.create_hand_made.infusion_gun.contents",
                            fluid.getName(),
                            fluid.getAmount() / InfusionGunContents.DROPLETS_PER_MB,
                            InfusionGunContents.CAPACITY_MB)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ================== 广播流体粒子 ==================

    private static void broadcastFluidParticles(Level level, BlockPos pos, FluidStack fluid) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (fluid.isEmpty()) return;

        FluidParticlesPacket packet = new FluidParticlesPacket(pos, fluid.copy());
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ServerPlayNetworking.send(p, packet);
            }
        }
    }
}

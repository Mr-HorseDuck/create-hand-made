package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.network.FluidParticlesPacket;
import com.alben.createhandmade.recipe.HandMadeFillingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
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
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber
public class InfusionGunItem extends Item {

    // ================== 交互参数（方便调优） ==================

    /** 长按阈值：超过此 tick 数视为"长按"，走吸分支 */
    private static final int LONG_PRESS_THRESHOLD = 8;

    /** 容器抽取（潜行长按）：单次抽取量（mB） */
    private static final int EXTRACT_RATE_MB = 25;

    /** 容器抽取（潜行长按）：单次抽取间隔（tick） */
    private static final int EXTRACT_INTERVAL_TICKS = 8;

    /** 音效响度 */
    private static final float FILL_SOUND_VOLUME = 0.7f;

    /** 流体方块：一整桶的量（mB） */
    private static final int FLUID_BLOCK_AMOUNT = 1000;

    /**
     * 潜行吸流体方块所需 tick 数（一次性完成）。
     *
     * <p>独立硬编码 40，不再用
     * {@code FLUID_BLOCK_AMOUNT / EXTRACT_RATE_MB * EXTRACT_INTERVAL_TICKS} 公式 ——
     * {@code EXTRACT_INTERVAL_TICKS} 改成 4（潜行容器的抽取节奏）之后，
     * 那条公式会算出 160，与实际想要的节奏无关。</p>
     */
    private static final int FLUID_BLOCK_PICKUP_TICKS = FLUID_BLOCK_AMOUNT / EXTRACT_RATE_MB * EXTRACT_INTERVAL_TICKS;

    // ================== 玩家状态 ==================

    private static final Map<UUID, PressInfo> PRESSING = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_EXTRACT = new HashMap<>();
    private static final Map<UUID, Boolean> EXTRACT_DAMAGED = new HashMap<>();

    /**
     * 本次长按是否已经触发过"流体方块一次性吸取"。
     * 用于配合 {@code usedTicks >= FLUID_BLOCK_PICKUP_TICKS} 的判定，保证只触发一次。
     */
    private static final Set<UUID> PICKUP_TRIGGERED = new HashSet<>();

    /**
     * 本次长按是否已经触发过"不潜行快速抽满容器"。
     * 与 {@link #PICKUP_TRIGGERED} 同理，保证一次长按只抽一次。
     */
    private static final Set<UUID> EXTRACT_TRIGGERED = new HashSet<>();

    /**
     * 玩家在 onRightClickBlock 时保存的交互上下文。
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

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================== 拦截右键：只做拦截，不记录状态 ==================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof InfusionGunItem)) return;

        Level level = event.getLevel();
        ItemStack gun = player.getMainHandItem();

        // ★ 主动做一次 SOURCE_ONLY 射线：如果玩家真正瞄的是流体源，就用它的位置。
        //   原版传给本事件的 event.getPos() 来自 Fluid.NONE 射线，会"穿透"水面
        //   命中水后面的方块。
        BlockPos pos;
        BlockHitResult fluidHit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (fluidHit.getType() == HitResult.Type.BLOCK && isFluidSource(level, fluidHit.getBlockPos())) {
            pos = fluidHit.getBlockPos();
        } else {
            pos = event.getPos();
        }

        if (!canInteractAt(level, pos, gun)) return;

        event.setUseBlock(TriState.FALSE);
        // ★ 这里不再记录 PRESSING —— 交给 use()。
        //   原因：本事件依赖原版 Fluid.NONE 射线，当流体源背后是空气时射线不命中任何方块，
        //   事件根本不会触发；而 use() 自己用 SOURCE_ONLY 射线能命中空中的流体源。
    }

    // ================== use：进入长按（并记录交互上下文） ==================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(gun);
        }
        if (!canInteractAt(level, hit.getBlockPos(), gun)) {
            return InteractionResultHolder.pass(gun);
        }

        if (!level.isClientSide) {
            // ★ 长按分支判定：潜行时优先流体方块；不潜行时优先容器
            PressInfo.InteractionMode mode;
            if (player.isShiftKeyDown()) {
                mode = isFluidSource(level, hit.getBlockPos())
                        ? PressInfo.InteractionMode.FLUID_BLOCK
                        : PressInfo.InteractionMode.CONTAINER;
            } else {
                IFluidHandler handler = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, hit.getBlockPos(), null);
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
        return InteractionResultHolder.consume(gun);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // ================== onUseTick：长按期间的处理 ==================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        PressInfo info = PRESSING.get(player.getUUID());
        if (info == null) return;

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (usedTicks < 3) return;

        boolean sneaking = player.isShiftKeyDown();

        if (info.mode() == PressInfo.InteractionMode.CONTAINER) {
            if (sneaking) {
                // 潜行：每 EXTRACT_INTERVAL_TICKS tick 抽 25mB（慢速精确抽）
                if (usedTicks % EXTRACT_INTERVAL_TICKS != 0) return;
                tryExtractTick(level, info.pos(), player, stack);
            } else {
                // 不潜行：达到 LONG_PRESS_THRESHOLD 后每 EXTRACT_INTERVAL_TICKS tick 尝试一次抽满；
                // 成功才置 flag 停止，失败则下个周期继续重试（例如枪内剩余空间不足时）
                if (usedTicks >= LONG_PRESS_THRESHOLD
                        && (usedTicks - LONG_PRESS_THRESHOLD) % EXTRACT_INTERVAL_TICKS == 0
                        && !EXTRACT_TRIGGERED.contains(player.getUUID())) {
                    if (tryExtractMax(level, info.pos(), player, stack)) {
                        EXTRACT_TRIGGERED.add(player.getUUID());
                    }
                }
            }
        } else {  // FLUID_BLOCK
            FluidStack source = getFluidFromSource(level, info.pos());

            if (sneaking) {
                // 潜行：保持 FLUID_BLOCK_PICKUP_TICKS(40) 一次性吸；音效 + 粒子按 EXTRACT_INTERVAL_TICKS 节奏播
                if (!source.isEmpty() && usedTicks % EXTRACT_INTERVAL_TICKS == 0) {
                    level.playSound(null, player.blockPosition(),
                            FluidHelper.getFillSound(source),
                            SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f);
                }
                if (!source.isEmpty()) {
                    spawnPickupProgressParticles(level, player, info.pos(), source);
                }
                if (usedTicks >= FLUID_BLOCK_PICKUP_TICKS
                        && !source.isEmpty()
                        && !PICKUP_TRIGGERED.contains(player.getUUID())) {
                    PICKUP_TRIGGERED.add(player.getUUID());
                    tryPickupFluidBlock(level, info.pos(), player, stack);
                }
            } else {
                // 不潜行：达到 LONG_PRESS_THRESHOLD 后每 EXTRACT_INTERVAL_TICKS tick 尝试一次立即吸；
                // 成功才置 flag + 播音效/粒子，失败则下个周期继续重试
                if (usedTicks >= LONG_PRESS_THRESHOLD
                        && (usedTicks - LONG_PRESS_THRESHOLD) % EXTRACT_INTERVAL_TICKS == 0
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
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        PENDING_EXTRACT.remove(player.getUUID());
        EXTRACT_DAMAGED.remove(player.getUUID());
        PICKUP_TRIGGERED.remove(player.getUUID());
        EXTRACT_TRIGGERED.remove(player.getUUID());

        PressInfo info = PRESSING.remove(player.getUUID());
        if (info == null) return;

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        if (usedTicks >= LONG_PRESS_THRESHOLD) return;   // 长按已由 onUseTick 处理

        // ★ 目标守卫：判断这个位置是否允许放流体源
        //   - 目标是流体源方块 → 不放（否则会在源旁边又生成一个新源，等于"吸了又放"）
        //   - 目标是容器（有 IFluidHandler）→ 不放（否则可能洒到容器旁边）
        //   - 其余（空气、石头等）→ 潜行与不潜行都允许，但优先级低于注入 / 存容器
        boolean isFluidSourcePos = isFluidSource(level, info.pos());
        boolean isContainer = level.getCapability(
                Capabilities.FluidHandler.BLOCK, info.pos(), null) != null;
        boolean canPlaceFluid = !isFluidSourcePos && !isContainer;

        // ★ 短按：按优先级尝试。注入物品 + 存容器是一组，注入优先。
        if (player.isShiftKeyDown()) {
            // 潜行：放流体方块 → 注入 → 存容器
            if (canPlaceFluid && tryPlaceFluidBlock(level, info.pos(), info.face(), player, stack)) return;
            if (tryInjectItems(level, info.pos(), player, stack)) return;
            tryStoreFluid(level, info.pos(), player, stack);
        } else {
            // 不潜行：注入 → 存容器 → 放流体方块
            if (tryInjectItems(level, info.pos(), player, stack)) return;
            if (tryStoreFluid(level, info.pos(), player, stack)) return;
            if (canPlaceFluid) tryPlaceFluidBlock(level, info.pos(), info.face(), player, stack);
        }
    }

    // ================== 抽取容器（累计判定，兼容整桶容器） ==================

    private static void tryExtractTick(Level level, BlockPos pos, Player player, ItemStack gun) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) return;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() <= 0) return;

        int pending = Math.min(PENDING_EXTRACT.getOrDefault(player.getUUID(), 0) + EXTRACT_RATE_MB, 10000);

        FluidStack source = FluidStack.EMPTY;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) continue;
            if (!contents.isEmpty() && !FluidStack.isSameFluidSameComponents(contents.fluid(), inTank)) {
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

        // ★ 音效放在 drain 之前 —— 只要源里有流体，就按周期给出"正在抽"的反馈。
        //   炼药锅这类按整桶出入的容器，drain(25mB) 会返回空；若把音效放在 drain 之后，
        //   就会被下面 actual.isEmpty() 的提前 return 吃掉，玩家长按听不到任何声音，
        //   会误以为工具坏了。
        // ★ 调用节奏已由 onUseTick 控制（潜行时每 EXTRACT_INTERVAL_TICKS tick 才调一次），
        //   所以这里只要有流体就播，音效与抽取同步 —— 第一次抽取（usedTicks=4）就有声音。
        if (!source.isEmpty()) {
            level.playSound(null, pos, FluidHelper.getFillSound(source),
                    SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f + level.random.nextFloat() * 0.2f);
        }

        FluidStack actual = handler.drain(source.copyWithAmount(want), IFluidHandler.FluidAction.EXECUTE);
        if (actual.isEmpty()) {
            // 容器抽不出（如整桶出入的容器），保留 pending 等下次
            PENDING_EXTRACT.put(player.getUUID(), pending);
            return;
        }

        PENDING_EXTRACT.put(player.getUUID(), Math.max(0, pending - actual.getAmount()));
        setContents(gun, contents.withFill(actual));

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
     * <p>不维护 {@code PENDING_EXTRACT} / {@code EXTRACT_DAMAGED}：
     * 抽一次就结束，扣一次耐久即可。</p>
     *
     * @return 真的抽到流体并写入枪内时为 true；任何前置检查失败或 drain 落空时为 false
     *         （调用方据此决定是否置位 {@code EXTRACT_TRIGGERED}，失败则下个周期重试）
     */
    private static boolean tryExtractMax(Level level, BlockPos pos, Player player, ItemStack gun) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() <= 0) return false;

        FluidStack source = FluidStack.EMPTY;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) continue;
            if (!contents.isEmpty()
                    && !FluidStack.isSameFluidSameComponents(contents.fluid(), inTank)) {
                continue;
            }
            source = inTank;
            break;
        }
        if (source.isEmpty()) return false;

        int want = Math.min(contents.remaining(), source.getAmount());
        if (want <= 0) return false;

        FluidStack actual = handler.drain(source.copyWithAmount(want), IFluidHandler.FluidAction.EXECUTE);
        if (actual.isEmpty()) return false;

        setContents(gun, contents.withFill(actual));
        damageGun(gun, player);

        level.playSound(null, pos, FluidHelper.getFillSound(actual),
                SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f + level.random.nextFloat() * 0.2f);

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
        if (!contents.isEmpty() && !FluidStack.isSameFluidSameComponents(contents.fluid(), source)) {
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
     * 只处理源方块 + 有桶形式的流体；否则返回空。
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
        BlockState fluidBlockState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidBlockState.isAir()) return FluidStack.EMPTY;

        return new FluidStack(fluid, FLUID_BLOCK_AMOUNT);
    }

    private static boolean isFluidSource(Level level, BlockPos pos) {
        return !getFluidFromSource(level, pos).isEmpty();
    }

    // ================== 放置流体方块 ==================

    /**
     * 把枪内 1000mB 流体放到点击面的相邻位置。
     * 严格判定：枪内 ≥1000mB、目标位置可替换、流体有方块形式。
     */
    private static boolean tryPlaceFluidBlock(Level level, BlockPos clickedPos, Direction face,
                                              Player player, ItemStack gun) {
        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;
        if (contents.amount() < FLUID_BLOCK_AMOUNT) return false;

        BlockPos targetPos = clickedPos.relative(face);
        BlockState targetState = level.getBlockState(targetPos);
        if (!targetState.canBeReplaced()) return false;

        Fluid fluid = contents.fluid().getFluid();
        if (fluid == Fluids.EMPTY) return false;

        // 检查流体是否有方块形式
        BlockState fluidBlockState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidBlockState.isAir()) return false;

        level.setBlock(targetPos, fluidBlockState, 11);
        level.playSound(null, targetPos, FluidHelper.getEmptySound(contents.fluid()),
                SoundSource.PLAYERS, 1.0f, 1.0f + level.random.nextFloat() * 0.2f);

        setContents(gun, contents.withDrain(FLUID_BLOCK_AMOUNT));
        damageGun(gun, player);
        return true;
    }

    /**
     * 在流体源方块上方生成"滴落"粒子。
     * 表现：从方块顶部冒出 2-3 个粒子，缓慢向下落——视觉上像水流被搅动/被吸走。
     */
    private static void spawnPickupProgressParticles(Level level, Player player,
                                                     BlockPos sourcePos, FluidStack fluid) {
        if (fluid.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ★ 用流体实际高度精确定位：基准必须是方块底面中心，
        //   用 atCenterOf 会再叠 0.5，粒子会飘到方块顶上 0.5 格
        double fluidHeight = level.getFluidState(sourcePos).getOwnHeight();
        Vec3 center = Vec3.atBottomCenterOf(sourcePos).add(0, fluidHeight + 0.1, 0);

        ParticleOptions particle = FluidFX.getFluidParticle(fluid);
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

    // ================== 注入物品（保留原逻辑） ==================

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
            RecipeHolder<? extends StandardProcessingRecipe<?>> recipe =
                    findFillingRecipe(level, stack, contents.fluid());
            if (recipe != null) {
                int required = requiredFluidOf(recipe.value()).amount();
                if (contents.fluid().getAmount() < required) return TransportedResult.doNothing();

                List<ItemStack> results = recipe.value().rollResults(level.random);

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
                    SoundSource.PLAYERS, 0.8f, 1.0f + level.random.nextFloat() * 0.2f);
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

    // ================== 存入流体容器（保留原逻辑） ==================

    private static boolean tryStoreFluid(Level level, BlockPos pos, Player player, ItemStack gun) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;

        FluidStack fluid = contents.fluid();
        int actual = handler.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (actual <= 0) return false;

        setContents(gun, contents.withDrain(actual));
        damageGun(gun, player);

        level.playSound(null, pos, FluidHelper.getEmptySound(fluid),
                SoundSource.PLAYERS, 1f, 1.0f + level.random.nextFloat() * 0.2f);
        return true;
    }

    // ================== 耐久 ==================

    private static void damageGun(ItemStack gun, Player player) {
        gun.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
    }

    // ================== 判定：能否交互 ==================

    private static boolean canInteractAt(Level level, BlockPos pos, ItemStack gun) {
        InfusionGunContents contents = getContents(gun);

        // 流体源方块：总是"可交互"（吸/放都由后续分支处理）
        if (isFluidSource(level, pos)) return true;

        // 容器：有液体可吸 或 枪有液体可存
        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidHandler != null) {
            if (!contents.isEmpty()) return true;
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                if (!fluidHandler.getFluidInTank(i).isEmpty()) return true;
            }
        }

        // 注入物品
        if (!contents.isEmpty() && canInjectItemsAt(level, pos, contents)) {
            return true;
        }

        // 放流体方块：枪内 ≥1000mB 且相邻位置有可放置的空位
        if (!contents.isEmpty() && contents.amount() >= FLUID_BLOCK_AMOUNT) {
            for (Direction dir : Direction.values()) {
                BlockPos target = pos.relative(dir);
                if (level.getBlockState(target).canBeReplaced()) {
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

    @Nullable
    private static RecipeHolder<? extends StandardProcessingRecipe<?>> findFillingRecipe(
            Level level, ItemStack target, FluidStack fluid) {
        SingleRecipeInput input = new SingleRecipeInput(target);

        // ★ 序列组装不走配方池：它需要按「输入 + 中间物品」的组装进度解析，
        //   不是一条普通的 FILLING 配方，保持原样（含它自带的 matches + 流体判定谓词）。
        Optional<RecipeHolder<FillingRecipe>> sequenced = SequencedAssemblyRecipe.getRecipe(
                level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
                r -> r.value().matches(input, level) && r.value().getRequiredFluid().test(fluid));
        if (sequenced.isPresent()) return sequenced.get();

        // 候选集改由统一配方池提供；匹配与流体判定留在本类。
        // 这里没有 CAN_BE_AUTOMATED 过滤 —— 改造前就没有，不新增。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.INFUSION_GUN, level)) {
            // L3 独占配方（注液家族）：只认归属灌注枪的那些。
            // 流体判定与 Create 分支一样留在工具侧（matches 只管物品）。
            if (holder.value() instanceof HandMadeFillingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.INFUSION_GUN) continue;
                if (!exclusive.matches(input, level)) continue;
                if (!exclusive.getRequiredFluid().test(fluid)) continue;
                return new RecipeHolder<>(holder.id(), exclusive);
            }

            if (!(holder.value() instanceof FillingRecipe fr)) continue;
            if (!fr.matches(input, level)) continue;
            if (!fr.getRequiredFluid().test(fluid)) continue;
            return new RecipeHolder<>(holder.id(), fr);
        }

        return null;
    }

    /**
     * 取「注液类」配方要求的流体。
     *
     * <p>Create 的 {@link FillingRecipe} 与本模组的 {@link HandMadeFillingRecipe} 都继承
     * {@code StandardProcessingRecipe}，但那个公共父类型上<b>没有</b> {@code getRequiredFluid()}
     * （Create 只把它放在 FillingRecipe 上），所以这里把两边的取法收拢到一处，
     * 调用点就不必各写一次 instanceof。</p>
     *
     * @throws IllegalStateException 传入的不是注液类配方（正常流程到不了这里）
     */
    private static SizedFluidIngredient requiredFluidOf(StandardProcessingRecipe<?> recipe) {
        if (recipe instanceof FillingRecipe filling) {
            return filling.getRequiredFluid();
        }
        if (recipe instanceof HandMadeFillingRecipe exclusive) {
            return exclusive.getRequiredFluid();
        }
        throw new IllegalStateException("Not a filling recipe: " + recipe);
    }

    // ================== 数据组件 ==================

    public static InfusionGunContents getContents(ItemStack gun) {
        InfusionGunContents contents = gun.get(ModDataComponents.INFUSION_GUN_CONTENTS.get());
        if (contents == null) {
            contents = new InfusionGunContents(FluidStack.EMPTY);
        }
        return contents;
    }

    public static void setContents(ItemStack gun, InfusionGunContents contents) {
        if (contents.isEmpty()) {
            gun.remove(ModDataComponents.INFUSION_GUN_CONTENTS.get());
        } else {
            gun.set(ModDataComponents.INFUSION_GUN_CONTENTS.get(), contents);
        }
    }

    // ================== 玩家下线清理 ==================

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        PRESSING.remove(uuid);
        PENDING_EXTRACT.remove(uuid);
        EXTRACT_DAMAGED.remove(uuid);
        PICKUP_TRIGGERED.remove(uuid);
        EXTRACT_TRIGGERED.remove(uuid);
    }

    // ================== Tooltip ==================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        InfusionGunContents contents = getContents(stack);

        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("item.create_hand_made.infusion_gun.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            FluidStack fluid = contents.fluid();
            tooltip.add(Component.translatable("item.create_hand_made.infusion_gun.contents",
                            fluid.getHoverName(),
                            fluid.getAmount(),
                            InfusionGunContents.CAPACITY)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new InfusionGunRenderer()));
    }

    // ================== 广播流体粒子 ==================

    private static void broadcastFluidParticles(Level level, BlockPos pos, FluidStack fluid) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (fluid.isEmpty()) return;

        FluidParticlesPacket packet = new FluidParticlesPacket(pos, fluid.copy());
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }
}
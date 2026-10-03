package com.alben.createhandmade.item;

import com.alben.createhandmade.network.FluidParticlesPacket;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;

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

@Mod.EventBusSubscriber
public class InfusionGunItem extends Item {

    private static final int LONG_PRESS_THRESHOLD = 4;
    /** 快抽：不潜行时每 tick 抽取量（mB） */
    private static final int EXTRACT_RATE = 50;
    /** 慢抽：潜行时单次抽取量（mB） */
    private static final int SLOW_EXTRACT_RATE = 25;
    /** 慢抽：潜行时抽取间隔（tick） */
    private static final int SLOW_EXTRACT_INTERVAL = 8;
    /** 水桶式吸取：蓄力所需 tick */
    private static final int FLUID_BLOCK_PICKUP_TICKS = 40;
    /** 水桶式：单次交换量（mB） */
    private static final int FLUID_BLOCK_AMOUNT = 1000;
    /** 音效响度 */
    private static final float FILL_SOUND_VOLUME = 0.7f;

    private static final Map<UUID, PressInfo> PRESSING = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_EXTRACT = new HashMap<>();
    private static final Map<UUID, Boolean> EXTRACT_DAMAGED = new HashMap<>();
    private static final Set<UUID> PICKUP_TRIGGERED = new HashSet<>();

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

    // ================= 拦截右键 =================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof InfusionGunItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        ItemStack gun = player.getMainHandItem();

        if (face == null) return;
        if (!canInteractAt(level, pos, face, player, gun)) return;

        event.setUseBlock(Event.Result.DENY);
    }

    private static boolean canInteractAt(Level level, BlockPos pos, Direction face,
                                          Player player, ItemStack gun) {
        // 流体源 → 水桶式收集
        if (isFluidSource(level, pos)) return true;

        // 容器 → 抽取/注入/存储
        IFluidHandler fluidHandler = getFluidHandler(level, pos);
        if (fluidHandler != null) {
            InfusionGunContents contents = getContents(gun);
            if (!contents.isEmpty()) return true;
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                if (!fluidHandler.getFluidInTank(i).isEmpty()) return true;
            }
        }

        // 注入物品
        InfusionGunContents contents = getContents(gun);
        if (!contents.isEmpty() && canInjectItemsAt(level, pos, contents)) {
            return true;
        }

        // 潜行 + 有流体 → 水桶式放置
        if (player.isShiftKeyDown() && !contents.isEmpty()) {
            if (canReplaceWithSource(level.getBlockState(pos.relative(face)))) return true;
        }

        return false;
    }

    /** ★ 女仆兼容：改为 public static */
    @Nullable
    public static IFluidHandler getFluidHandler(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        LazyOptional<IFluidHandler> opt = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
        return opt.orElse(null);
    }

    // ================= use =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(gun);
        }

        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();

        // ★ 水桶式收集：点击流体源
        if (isFluidSource(level, pos)) {
            if (!level.isClientSide) {
                PRESSING.put(player.getUUID(),
                        new PressInfo(pos, PressInfo.InteractionMode.FLUID_BLOCK, face));
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(gun);
        }

        // ★ 水桶式放置：潜行 + 有流体
        InfusionGunContents contents = getContents(gun);
        if (player.isShiftKeyDown() && !contents.isEmpty()
                && canReplaceWithSource(level.getBlockState(pos.relative(face)))) {
            if (!level.isClientSide) {
                PRESSING.put(player.getUUID(),
                        new PressInfo(pos, PressInfo.InteractionMode.FLUID_BLOCK, face));
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(gun);
        }

        // ★ 容器交互
        if (!canInteractAt(level, pos, face, player, gun)) {
            return InteractionResultHolder.pass(gun);
        }

        if (!level.isClientSide) {
            PRESSING.put(player.getUUID(),
                    new PressInfo(pos, PressInfo.InteractionMode.CONTAINER, face));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(gun);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // ================= 长按 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        PressInfo info = PRESSING.get(player.getUUID());
        if (info == null) return;

        int usedTicks = getUseDuration(stack) - remainingTicks;
        if (usedTicks < 3) return;

        if (info.mode() == PressInfo.InteractionMode.CONTAINER) {
            // 容器模式：抽取
            boolean sneaking = player.isShiftKeyDown();
            if (sneaking) {
                if (usedTicks % SLOW_EXTRACT_INTERVAL != 0) return;
                tryExtractTick(level, info.pos(), player, stack, SLOW_EXTRACT_RATE);
            } else {
                tryExtractTick(level, info.pos(), player, stack, EXTRACT_RATE);
            }
        } else {
            // 流体方块模式：蓄力收集
            if (!isFluidSource(level, info.pos())) return;
            if (usedTicks >= FLUID_BLOCK_PICKUP_TICKS
                    && !PICKUP_TRIGGERED.contains(player.getUUID())) {
                if (tryPickupFluidBlock(level, info.pos(), player, stack)) {
                    PICKUP_TRIGGERED.add(player.getUUID());
                }
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        PENDING_EXTRACT.remove(player.getUUID());
        EXTRACT_DAMAGED.remove(player.getUUID());
        PICKUP_TRIGGERED.remove(player.getUUID());

        PressInfo info = PRESSING.remove(player.getUUID());
        if (info == null) return;

        int usedTicks = getUseDuration(stack) - timeLeft;
        if (usedTicks >= LONG_PRESS_THRESHOLD) return;

        // ★ 水桶式放置：流体方块模式 + 非流体源 + 短按
        if (info.mode() == PressInfo.InteractionMode.FLUID_BLOCK
                && !isFluidSource(level, info.pos())) {
            if (tryPlaceFluidBlock(level, info.pos(), info.face(), player, stack)) return;
        }

        // 容器模式：注入物品 → 存入流体
        if (!tryInjectItems(level, info.pos(), player, stack)) {
            tryStoreFluid(level, info.pos(), player, stack);
        }
    }

    // ================= 水桶式：放置流体方块 =================

    private static boolean tryPlaceFluidBlock(Level level, BlockPos pos, Direction face,
                                              Player player, ItemStack gun) {
        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty() || contents.fluid().getAmount() < FLUID_BLOCK_AMOUNT) return false;

        BlockPos target = pos.relative(face);
        BlockState targetState = level.getBlockState(target);
        if (!canReplaceWithSource(targetState)) return false;

        FluidStack fluid = contents.fluid();
        Fluid sourceFluid = fluid.getFluid();
        BlockState sourceState = sourceFluid.defaultFluidState().createLegacyBlock();
        if (sourceState.isAir()) return false;

        level.setBlockAndUpdate(target, sourceState);
        setContents(gun, contents.withDrain(FLUID_BLOCK_AMOUNT));
        damageGun(gun, player);

        level.playSound(null, target, FluidHelper.getEmptySound(fluid),
                SoundSource.PLAYERS, 1.0f, 1.0f + level.random.nextFloat() * 0.2f);

        return true;
    }

    // ================= 水桶式：吸取流体方块 =================

    private static boolean tryPickupFluidBlock(Level level, BlockPos pos, Player player, ItemStack gun) {
        FluidStack source = getFluidFromSource(level, pos);
        if (source.isEmpty()) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() < FLUID_BLOCK_AMOUNT) return false;

        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        setContents(gun, contents.withFill(source));
        damageGun(gun, player);

        level.playSound(null, pos, FluidHelper.getFillSound(source),
                SoundSource.PLAYERS, FILL_SOUND_VOLUME, 1.0f + level.random.nextFloat() * 0.2f);

        return true;
    }

    private static boolean isFluidSource(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.isSource() && state.getType() != Fluids.EMPTY;
    }

    private static FluidStack getFluidFromSource(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        if (!state.isSource() || state.getType() == Fluids.EMPTY) return FluidStack.EMPTY;
        return new FluidStack(state.getType(), FLUID_BLOCK_AMOUNT);
    }

    private static boolean canReplaceWithSource(BlockState state) {
        if (state.isAir()) return true;
        if (state.canBeReplaced()) return true;
        return state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.LAVA;
    }

    // ================= 累计抽取 =================

    /** ★ 女仆兼容：改为 public static，Player → LivingEntity */
    public static void tryExtractTick(Level level, BlockPos pos, LivingEntity entity, ItemStack gun, int rate) {
        IFluidHandler handler = getFluidHandler(level, pos);
        if (handler == null) return;

        InfusionGunContents contents = getContents(gun);
        if (contents.remaining() <= 0) return;

        int pending = Math.min(PENDING_EXTRACT.getOrDefault(entity.getUUID(), 0) + rate, 10000);

        FluidStack source = FluidStack.EMPTY;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) continue;
            if (!contents.isEmpty() && !FluidStack.areFluidStackTagsEqual(contents.fluid(), inTank)) {
                continue;
            }
            source = inTank;
            break;
        }

        if (source.isEmpty()) {
            PENDING_EXTRACT.remove(entity.getUUID());
            return;
        }

        int want = Math.min(Math.min(pending, contents.remaining()), source.getAmount());
        if (want <= 0) return;

        FluidStack toDrain = new FluidStack(source.getFluid(), want, source.getTag());
        FluidStack actual = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (actual.isEmpty()) {
            PENDING_EXTRACT.put(entity.getUUID(), pending);
            return;
        }

        PENDING_EXTRACT.put(entity.getUUID(), Math.max(0, pending - actual.getAmount()));
        setContents(gun, contents.withFill(actual));

        if (!EXTRACT_DAMAGED.getOrDefault(entity.getUUID(), false)) {
            damageGun(gun, entity);
            EXTRACT_DAMAGED.put(entity.getUUID(), true);
        }

        if (level.getGameTime() % 10 == 0) {
            level.playSound(null, pos, FluidHelper.getFillSound(actual),
                    SoundSource.PLAYERS, 0.4f, 1.0f + level.random.nextFloat() * 0.2f);
        }
    }

    // ================= 注入物品 =================

    /** ★ 女仆兼容：改为 public static，Player → LivingEntity */
    public static boolean tryInjectItems(Level level, BlockPos pos, LivingEntity entity, ItemStack gun) {
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

            FillingRecipe recipe = findFillingRecipe(level, stack, contents.fluid());
            if (recipe != null) {
                List<FluidStack> matching = recipe.getRequiredFluid().getMatchingFluidStacks();
                int required = matching.isEmpty() ? 0 : matching.get(0).getAmount();
                if (contents.fluid().getAmount() < required) return TransportedResult.doNothing();

                List<ItemStack> results = recipe.rollResults();

                success[0] = true;
                setContents(gun, contents.withDrain(required));
                broadcastFluidParticles(level, pos, contents.fluid());

                return finishInject(item, stack, results);
            }

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
            damageGun(gun, entity);
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

    // ================= 存入流体 =================

    /** ★ 女仆兼容：改为 public static，Player → LivingEntity */
    public static boolean tryStoreFluid(Level level, BlockPos pos, LivingEntity entity, ItemStack gun) {
        IFluidHandler handler = getFluidHandler(level, pos);
        if (handler == null) return false;

        InfusionGunContents contents = getContents(gun);
        if (contents.isEmpty()) return false;

        FluidStack fluid = contents.fluid();
        int actual = handler.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (actual <= 0) return false;

        setContents(gun, contents.withDrain(actual));
        damageGun(gun, entity);

        level.playSound(null, pos, FluidHelper.getEmptySound(fluid),
                SoundSource.PLAYERS, 1f, 1.0f + level.random.nextFloat() * 0.2f);

        return true;
    }

    // ================= 耐久 =================

    /** ★ 女仆兼容：改为 public static，Player → LivingEntity，内部判断 */
    public static void damageGun(ItemStack gun, LivingEntity entity) {
        if (entity instanceof Player player) {
            EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            gun.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));
        } else {
            // 女仆：直接扣耐久，不广播手臂动画
            gun.hurtAndBreak(1, entity, e -> {});
        }
    }

    // ================= 判定 =================

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

    // ================= 三层配方查询 =================

    @Nullable
    private static FillingRecipe findFillingRecipe(Level level, ItemStack target, FluidStack fluid) {
        if (target.isEmpty()) return null;

        List<Recipe<?>> custom = HandMadeRecipePool.getCustomRecipes(
                ToolType.INFUSION_GUN, level, target);
        for (Recipe<?> r : custom) {
            if (!(r instanceof FillingRecipe fr)) continue;
            if (!fr.getRequiredFluid().test(fluid)) continue;
            return fr;
        }

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, target.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        FillingRecipe result = null;

        Optional<FillingRecipe> sequenced = SequencedAssemblyRecipe.getRecipe(
                level, wrapper, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
                r -> r.matches(wrapper, level) && r.getRequiredFluid().test(fluid));
        if (sequenced.isPresent()) {
            result = sequenced.get();
        } else {
            for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.FILLING.getType())) {
                if (!(r instanceof FillingRecipe fr)) continue;
                if (!fr.matches(wrapper, level)) continue;
                if (!fr.getRequiredFluid().test(fluid)) continue;
                result = fr;
                break;
            }
        }

        if (result == null) return null;

        List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                ToolType.INFUSION_GUN, level, List.of(result));
        return filtered.isEmpty() ? null : (FillingRecipe) filtered.get(0);
    }

    // ================= 数据组件 =================

    public static InfusionGunContents getContents(ItemStack gun) {
        InfusionGunContents contents = InfusionGunContents.fromStack(gun);
        if (contents == null) {
            contents = new InfusionGunContents(FluidStack.EMPTY);
        }
        return contents;
    }

    public static void setContents(ItemStack gun, InfusionGunContents contents) {
        if (contents.isEmpty()) {
            InfusionGunContents.clearFromStack(gun);
        } else {
            contents.writeToStack(gun);
        }
    }

    // ================= 玩家下线清理 =================

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        PRESSING.remove(uuid);
        PENDING_EXTRACT.remove(uuid);
        EXTRACT_DAMAGED.remove(uuid);
        PICKUP_TRIGGERED.remove(uuid);
    }

    // ================= Tooltip =================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        InfusionGunContents contents = getContents(stack);

        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("item.create_hand_made.infusion_gun.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            FluidStack fluid = contents.fluid();
            tooltip.add(Component.translatable("item.create_hand_made.infusion_gun.contents",
                            fluid.getDisplayName(),
                            fluid.getAmount(),
                            InfusionGunContents.CAPACITY)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new InfusionGunRenderer()));
    }

    // ================= 广播流体粒子 =================

    private static void broadcastFluidParticles(Level level, BlockPos pos, FluidStack fluid) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (fluid.isEmpty()) return;

        FluidParticlesPacket packet = new FluidParticlesPacket(pos, fluid.copy());
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }
}
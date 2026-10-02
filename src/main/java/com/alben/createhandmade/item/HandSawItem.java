package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class HandSawItem extends Item {

    private static final int CUT_MOVE_TICKS = 10;
    private static final int CUT_VIBRATE_TICKS = 20;
    private static final int CUT_DURATION = CUT_MOVE_TICKS + CUT_VIBRATE_TICKS;

    private static final int FELL_SOUND_INTERVAL = 4;

    /** ★ 1.20.1：用 NBT key 替代 DataComponentType */
    private static final String NBT_RECIPE_INDEX = "HandSawRecipeIndex";

    private static final Set<UUID> CUTTING_PLAYERS = new HashSet<>();
    private static final Map<UUID, BlockPos> FELL_PENDING = new HashMap<>();

    public HandSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 斧头动作支持（剥离/刮蜡/除锈） =================

    /**
     * ★ 关键修复：让手锯支持斧头动作
     *   getToolModifiedState(...) 内部会检查 canPerformAction(AXE_STRIP)，
     *   不重写这个方法，剥离/刮蜡/除锈都会失败。
     */
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction)
                || super.canPerformAction(stack, toolAction);
    }

    // ================= 斧头挖掘能力 =================

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return 6.0f;
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new HandSawItemRenderer()));
    }

    // ================= 使用动画 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return CUT_DURATION;
    }

    // ================= 攻击扣耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof HandSawItem) {
            mainHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof HandSawItem) {
            offHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
        }
    }

    // ================= 左键 START：潜行 + 可锯方块 → 记录候选目标 =================

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;

        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos pos = event.getPos();

        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            if (!player.isShiftKeyDown()) return;

            // ★ 配置关闭时不记录候选目标
            if (!Config.INSTANCE.enableTreeFelling.get()) return;

            BlockState state = level.getBlockState(pos);
            if (!SawBlockEntity.isSawable(state)) return;

            FELL_PENDING.put(player.getUUID(), pos.immutable());
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            FELL_PENDING.remove(player.getUUID());
        }
    }

    // ================= 服务端 tick：砍树循环音效 =================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (FELL_PENDING.isEmpty()) return;

        // ★ 1.20.1：event.server 是 private，改用 ServerLifecycleHooks
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean playSound = (server.getTickCount() % FELL_SOUND_INTERVAL == 0);

        Iterator<Map.Entry<UUID, BlockPos>> it = FELL_PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            if (!player.isShiftKeyDown()
                    || !(player.getMainHandItem().getItem() instanceof HandSawItem)) {
                it.remove();
                continue;
            }

            if (!playSound) continue;

            BlockPos pos = entry.getValue();
            player.level().playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                    SoundSource.PLAYERS, 1f,
                    0.7f + player.level().random.nextFloat() * 0.2f);
        }
    }

    // ================= 方块真正被挖碎时：判定并触发整树砍伐 =================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;
        if (!player.isShiftKeyDown()) return;

        // ★ 配置关闭时直接返回，不触发整树砍伐
        if (!Config.INSTANCE.enableTreeFelling.get()) return;

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!SawBlockEntity.isSawable(state)) return;

        BlockPos pending = FELL_PENDING.get(player.getUUID());
        if (pending == null || !pending.equals(pos)) return;

        FELL_PENDING.remove(player.getUUID());

        ItemStack saw = player.getMainHandItem();
        BlockPos immutablePos = pos.immutable();
        BlockState stateSnapshot = state;

        serverLevel.getServer().execute(() ->
                fellTreeFromBroken(serverLevel, player, saw, immutablePos, stateSnapshot));
    }

    // ================= 右键 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack saw = player.getItemInHand(hand);
        ItemStack off = player.getOffhandItem();

        if (player.isShiftKeyDown() && !off.isEmpty()) {
            if (!level.isClientSide) {
                switchRecipe(level, player, saw, off);
            }
            return InteractionResultHolder.success(saw);
        }

        if (!off.isEmpty() && !getCuttingRecipes(level, off).isEmpty()) {
            CUTTING_PLAYERS.add(player.getUUID());
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(saw);
        }

        return InteractionResultHolder.pass(saw);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack saw = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack off = player.getOffhandItem();

        if (player.isShiftKeyDown() && !off.isEmpty()) {
            return switchRecipe(level, player, saw, off);
        }

        if (!off.isEmpty() && !getCuttingRecipes(level, off).isEmpty()) {
            CUTTING_PLAYERS.add(player.getUUID());
            player.startUsingItem(context.getHand());
            return InteractionResult.CONSUME;
        }

        if (canStrip(state, context)) {
            if (!level.isClientSide) {
                stripBlock(level, player, saw, pos, context.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return handleAxeAction(context);
    }

    // ================= 切削 tick：服务端广播音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (!CUTTING_PLAYERS.contains(player.getUUID())) return;

        int usedTicks = getUseDuration(stack) - remainingTicks;
        if (usedTicks < CUT_MOVE_TICKS) return;
        if (usedTicks % 4 != 0) return;

        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                    SoundSource.PLAYERS, 0.8f,
                    1.4f + level.random.nextFloat() * 0.2f);

            if (level instanceof ServerLevel serverLevel) {
                Vec3 eye = entity.getEyePosition(1f);
                Vec3 look = entity.getLookAngle();
                Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2;
                    double speed = 0.05 + level.random.nextDouble() * 0.1;

                    serverLevel.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, off),
                            spawnPos.x, spawnPos.y, spawnPos.z,
                            1,
                            Math.cos(angle) * speed,
                            0.15 + level.random.nextDouble() * 0.15,
                            Math.sin(angle) * speed,
                            0.0
                    );
                }
            }
        }
    }

    // ================= finishUsingItem：切削完成 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        boolean wasCutting = CUTTING_PLAYERS.remove(player.getUUID());
        if (!wasCutting) return stack;
        if (level.isClientSide) return stack;

        executeCut(level, player, stack);
        return stack;
    }

    // ================= releaseUsing：切削取消 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        CUTTING_PLAYERS.remove(player.getUUID());
    }

    // ================= 切削执行 =================

    private static void executeCut(Level level, Player player, ItemStack saw) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        List<Recipe<?>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return;

        int index = saw.getOrCreateTag().getInt(NBT_RECIPE_INDEX);
        if (index < 0 || index >= recipes.size()) index = 0;

        Recipe<?> recipe = recipes.get(index);

        List<ItemStack> results = new ArrayList<>();
        if (recipe instanceof CuttingRecipe cr) {
            results = cr.rollResults();
        } else {
            results.add(recipe.getResultItem(level.registryAccess()).copy());
        }

        off.shrink(1);

        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            if (!player.getInventory().add(result.copy())) {
                player.drop(result.copy(), false);
            }
        }

        EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        saw.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));

        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    private static List<Recipe<?>> getCuttingRecipes(Level level, ItemStack input) {
        if (input.isEmpty()) return List.of();

        Optional<CuttingRecipe> assembly = SequencedAssemblyRecipe.getRecipe(
                level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
        if (assembly.isPresent()) {
            return List.of(assembly.get());
        }

        List<Recipe<?>> result = new ArrayList<>();

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        RecipeType<CuttingRecipe> cuttingType = AllRecipeTypes.CUTTING.getType();

        for (CuttingRecipe recipe : level.getRecipeManager().getAllRecipesFor(cuttingType)) {
            if (!recipe.matches(wrapper, level)) continue;
            if (!AllRecipeTypes.shouldIgnoreInAutomation(recipe)) {
                result.add(recipe);
            }
        }

        return result;
    }

    // ================= 整树砍伐 =================
    private static void fellTreeFromBroken(Level level, Player player, ItemStack saw,
                                           BlockPos pos, BlockState brokenState) {
        if (!(level instanceof ServerLevel)) return;
        if (!SawBlockEntity.isSawable(brokenState)) return;

        if (pos.distSqr(player.blockPosition()) > 64L * 64L) return;

        TreeCutter.Tree tree = TreeCutter.findTree(level, pos, brokenState);
        if (tree != TreeCutter.NO_TREE) {
            tree.destroyBlocks(level, null, (dropPos, dropStack) -> {
                if (dropStack.isEmpty()) return;
                Block.popResource(level, dropPos, dropStack);
            });
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    // ================= 去皮 =================

    private static boolean canStrip(BlockState state, UseOnContext context) {
        return state.getToolModifiedState(context, ToolActions.AXE_STRIP, false) != null;
    }

    private static void stripBlock(Level level, Player player, ItemStack saw,
                                   BlockPos pos, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);

        UseOnContext ctx = new UseOnContext(level, player, hand, saw,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

        BlockState stripped = state.getToolModifiedState(ctx, ToolActions.AXE_STRIP, false);
        if (stripped == null) return;

        level.setBlock(pos, stripped, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, stripped));
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        saw.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));
    }

    // ================= 切换配方 =================

    private static InteractionResult switchRecipe(Level level, Player player, ItemStack saw, ItemStack off) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        List<Recipe<?>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.create_hand_made.hand_saw.no_recipe")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        int current = saw.getOrCreateTag().getInt(NBT_RECIPE_INDEX);
        int next = (current + 1) % recipes.size();
        saw.getOrCreateTag().putInt(NBT_RECIPE_INDEX, next);

        Recipe<?> recipe = recipes.get(next);
        ItemStack result = recipe.getResultItem(level.registryAccess());

        player.displayClientMessage(
                Component.translatable("item.create_hand_made.hand_saw.recipe_selected",
                                result.getHoverName(), next + 1, recipes.size())
                        .withStyle(ChatFormatting.GREEN), true);

        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.get(),
                SoundSource.PLAYERS, 0.5f, 1.4f);

        return InteractionResult.SUCCESS;
    }

    // ================= 斧头兜底 =================

    private InteractionResult handleAxeAction(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockState state = level.getBlockState(pos);
        Optional<BlockState> newState = evaluateAxeAction(level, pos, player, state, context);
        if (newState.isEmpty()) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        level.setBlock(pos, newState.get(), 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState.get()));

        EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private Optional<BlockState> evaluateAxeAction(Level level, BlockPos pos,
                                                   @Nullable Player player, BlockState state,
                                                   UseOnContext context) {
        Optional<BlockState> stripped = Optional.ofNullable(
                state.getToolModifiedState(context, ToolActions.AXE_STRIP, false));
        if (stripped.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
            return stripped;
        }

        Optional<BlockState> scraped = Optional.ofNullable(
                state.getToolModifiedState(context, ToolActions.AXE_SCRAPE, false));
        if (scraped.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.levelEvent(player, 3005, pos, 0);
            return scraped;
        }

        Optional<BlockState> waxedOff = Optional.ofNullable(
                state.getToolModifiedState(context, ToolActions.AXE_WAX_OFF, false));
        if (waxedOff.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.levelEvent(player, 3004, pos, 0);
            return waxedOff;
        }

        return Optional.empty();
    }
}
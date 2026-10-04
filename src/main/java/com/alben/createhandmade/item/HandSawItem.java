package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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

    public static final String NBT_RECIPE_INDEX = "HandSawRecipeIndex";

    private static final Set<UUID> CUTTING_PLAYERS = new HashSet<>();
    private static final Map<UUID, BlockPos> FELL_PENDING = new HashMap<>();

    public HandSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction)
                || super.canPerformAction(stack, toolAction);
    }

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

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;

        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos pos = event.getPos();

        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            if (!player.isShiftKeyDown()) return;
            if (!Config.INSTANCE.enableTreeFelling.get()) return;

            BlockState state = level.getBlockState(pos);
            if (!SawBlockEntity.isSawable(state)) return;

            FELL_PENDING.put(player.getUUID(), pos.immutable());
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            FELL_PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (FELL_PENDING.isEmpty()) return;

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

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;
        if (!player.isShiftKeyDown()) return;
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

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        boolean wasCutting = CUTTING_PLAYERS.remove(player.getUUID());
        if (!wasCutting) return stack;
        if (level.isClientSide) return stack;

        // ★ 玩家手动触发时不用过滤器
        executeCut(level, player, stack, null);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        CUTTING_PLAYERS.remove(player.getUUID());
    }

    // ================= 切削执行 =================

    /**
     * ★ 女仆兼容：
     *   - public static，供 TLM 行为类调用
     *   - @Nullable FilterItemStack filter：过滤**产物**，null 表示不过滤
     *   - 返回 boolean 表示是否成功切削
     */
    public static boolean executeCut(Level level, LivingEntity entity, ItemStack saw,
                                     @Nullable FilterItemStack filter) {
        ItemStack off = entity.getOffhandItem();
        if (off.isEmpty()) return false;

        List<Recipe<?>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return false;

        // ★ 选出要执行的配方
        Recipe<?> recipe = null;
        List<ItemStack> results = new ArrayList<>();

        if (filter != null) {
            // 有过滤器：遍历所有配方，找产物通过过滤的第一个
            for (Recipe<?> candidate : recipes) {
                List<ItemStack> candidateResults = new ArrayList<>();
                if (candidate instanceof CuttingRecipe cr) {
                    candidateResults = cr.rollResults();
                } else {
                    candidateResults.add(candidate.getResultItem(level.registryAccess()).copy());
                }
                if (resultsPassFilter(level, candidateResults, filter)) {
                    recipe = candidate;
                    results = candidateResults;
                    break;
                }
            }
            if (recipe == null) return false;
        } else {
            // 无过滤器：用当前选中索引
            int index = saw.getOrCreateTag().getInt(NBT_RECIPE_INDEX);
            if (index < 0 || index >= recipes.size()) index = 0;
            recipe = recipes.get(index);

            if (recipe instanceof CuttingRecipe cr) {
                results = cr.rollResults();
            } else {
                results.add(recipe.getResultItem(level.registryAccess()).copy());
            }
        }

        off.shrink(1);

        for (ItemStack result : results) {
            if (result.isEmpty()) continue;

            if (entity instanceof Player player) {
                if (!player.getInventory().add(result.copy())) {
                    player.drop(result.copy(), false);
                }
            } else {
                entity.spawnAtLocation(result.copy());
            }
        }

        if (entity instanceof Player player) {
            EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            saw.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));
        } else {
            saw.hurtAndBreak(1, entity, e -> {});
        }

        level.playSound(null, entity.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.7f, 1.2f);

        return true;
    }

    // ================= 过滤器辅助 =================

    private static boolean resultsPassFilter(Level level, List<ItemStack> results,
                                             @Nullable FilterItemStack filter) {
        if (filter == null) return true;
        for (ItemStack s : results) {
            if (!s.isEmpty() && filter.test(level, s)) return true;
        }
        return false;
    }

    // ================= 三层配方查询 =================

    public static List<Recipe<?>> getCuttingRecipes(Level level, ItemStack input) {
        if (input.isEmpty()) return List.of();

        List<Recipe<?>> custom = HandMadeRecipePool.getCustomRecipes(
                ToolType.HAND_SAW, level, input);
        if (!custom.isEmpty()) return custom;

        List<Recipe<?>> result = new ArrayList<>();

        Optional<CuttingRecipe> assembly = SequencedAssemblyRecipe.getRecipe(
                level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
        if (assembly.isPresent()) {
            result.add(assembly.get());
        } else {
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
        }

        return HandMadeRecipePool.applyFilter(ToolType.HAND_SAW, level, result);
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
package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.recipe.HandMadeCuttingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
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

@EventBusSubscriber
public class HandSawItem extends Item {

    private static final int CUT_MOVE_TICKS = 10;
    private static final int CUT_VIBRATE_TICKS = 20;
    private static final int CUT_DURATION = CUT_MOVE_TICKS + CUT_VIBRATE_TICKS;

    /** 砍树循环音效间隔（tick） */
    private static final int FELL_SOUND_INTERVAL = 4;

    private static final Set<UUID> CUTTING_PLAYERS = new HashSet<>();
    private static final Map<UUID, BlockPos> FELL_PENDING = new HashMap<>();

    public HandSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ★ 已移除 canPerformAction 方法，手锯不再具有破盾能力
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new HandSawItemRenderer()));
    }

    // ================= 使用动画 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // ★ 连续切削：使用时长恒为"无限"（72000），真正的结算周期由 onUseTick 按 CUT_DURATION 控制，
        //   直到副手没料或玩家松手。
        //   必须是常量：LivingEntity.getTicksUsingItem() = getUseDuration() - useItemRemaining，
        //   渲染器（HandSawItemRenderer）依赖它算一阶段/二阶段进度。
        return 72000;
    }

    // ================= 攻击扣耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof HandSawItem) {
            mainHand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof HandSawItem) {
            offHand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
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
            // ★ 配置开关：关闭时完全不进入「待砍」状态 —— 不记录候选、不播锯木音效，
            //   潜行左键原木等同普通挖掘。与 fellTreeFromBroken 里的判定保持一致。
            if (!Config.INSTANCE.enableTreeFelling.get()) return;

            if (!player.isShiftKeyDown()) return;

            BlockState state = level.getBlockState(pos);
            if (!SawBlockEntity.isSawable(state)) return;

            FELL_PENDING.put(player.getUUID(), pos.immutable());
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            FELL_PENDING.remove(player.getUUID());
        }
    }

    // ================= 服务端 tick：砍树循环音效（同步给附近玩家） =================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (FELL_PENDING.isEmpty()) return;

        boolean playSound = (event.getServer().getTickCount() % FELL_SOUND_INTERVAL == 0);

        Iterator<Map.Entry<UUID, BlockPos>> it = FELL_PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            // 玩家不再满足条件 → 移除
            if (!player.isShiftKeyDown()
                    || !(player.getMainHandItem().getItem() instanceof HandSawItem)) {
                it.remove();
                continue;
            }

            if (!playSound) continue;

            // ★ 服务端播放锯木声，自动同步给 16 格内的所有玩家（包括自己）
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

        // ★ 耐久耗尽：原版 hurtAndBreak 会把锯子栈清空（count → 0），但 LivingEntity.updatingUsingItem()
        //   判定是否继续使用用的是 ItemStack.isSameItem()（只比 item，不比耐久/组件），所以使用状态
        //   不会自动结束 —— 不在这里拦住就会"空手"继续结算（材料照扣、产物照出）。
        if (stack.isEmpty()) {
            player.stopUsingItem();
            CUTTING_PLAYERS.remove(player.getUUID());
            return;
        }

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;

        // ★ 连续切削：每 CUT_DURATION tick 结算一份
        if (usedTicks > 0 && usedTicks % CUT_DURATION == 0) {
            if (!level.isClientSide) {
                executeCut(level, player, stack);
            }
            // 切完这一份后，副手没料 / 已无配方 → 两端停止使用
            ItemStack offAfterCut = player.getOffhandItem();
            if (offAfterCut.isEmpty() || getCuttingRecipes(level, offAfterCut).isEmpty()) {
                player.stopUsingItem();
                CUTTING_PLAYERS.remove(player.getUUID());
                return;
            }
        }

        if (usedTicks < CUT_MOVE_TICKS) return;
        if (usedTicks % 4 != 0) return;

        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        // ★ 服务端：音效 + 粒子广播给附近玩家
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

    // ================= finishUsingItem：仅清理状态 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // ★ 结算已移到 onUseTick（按 CUT_DURATION 周期）。getUseDuration() = 72000 后这里基本不会
        //   自然触发（原版只在 useItemRemaining 归零时调用，且仅服务端），保留清理只为安全。
        if (!(entity instanceof Player player)) return stack;

        CUTTING_PLAYERS.remove(player.getUUID());
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

        List<RecipeHolder<? extends Recipe<?>>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return;

        int index = saw.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX.get(), 0);
        if (index >= recipes.size()) index = 0;

        Recipe<?> recipe = recipes.get(index).value();

        List<ItemStack> results = new ArrayList<>();
        // L3 独占配方（切削家族）与 Create 的 CuttingRecipe 一样支持多产物 / 概率产物，
        // 所以必须走 rollResults；只有都不是时才退回"取第一个产物"的兜底分支。
        if (recipe instanceof HandMadeCuttingRecipe exclusive) {
            results = exclusive.rollResults(level.random);
        } else if (recipe instanceof CuttingRecipe cr) {
            results = cr.rollResults(level.random);
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

        saw.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));

        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    public static List<RecipeHolder<? extends Recipe<?>>> getCuttingRecipes(Level level, ItemStack input) {
        if (input.isEmpty()) return List.of();

        // ★ 序列组装不走配方池：它需要按「输入 + 中间物品」的组装进度解析，
        //   不是一条普通的 CUTTING 配方，保持原样。
        Optional<RecipeHolder<CuttingRecipe>> assembly = SequencedAssemblyRecipe.getRecipe(
                level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
        if (assembly.isPresent()) {
            return List.of(assembly.get());
        }

        List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        // 候选集改由统一配方池提供；匹配判定仍在本类。
        // 这里用 instanceof 取出具体的 CuttingRecipe 再调 matches：
        // holder.value() 的静态类型是 Recipe<?>，其 matches 的参数是通配符捕获，
        // 无法直接接受 RecipeWrapper，而 instanceof 模式匹配能拿到确切类型，
        // 既不需要 unchecked 强转，也不会在将来类型变化时静默出错。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.HAND_SAW, level)) {
            // ★ automation 过滤保留在调用方：配方池与 JEI 都不过滤这些"仅手动"配方，
            //   只有游戏内的手锯需要排除它们，行为与改造前一致。L3 独占配方同样受它管辖。
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;

            // L3 独占配方（切削家族）：只认归属手锯的那些
            if (holder.value() instanceof HandMadeCuttingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.HAND_SAW) continue;
                if (!exclusive.matches(wrapper, level)) continue;
                result.add(holder);
                continue;
            }

            if (!(holder.value() instanceof CuttingRecipe cuttingRecipe)) continue;
            if (!cuttingRecipe.matches(wrapper, level)) continue;
            result.add(holder);
        }

        return result;
    }

    // ================= 整树砍伐 =================
    private static void fellTreeFromBroken(Level level, Player player, ItemStack saw,
                                           BlockPos pos, BlockState brokenState) {
        // ★ 配置开关：关闭时只破坏当前方块（原版行为）。
        //   放在任何整树扫描之前早退，零开销；不影响切削配方 / 剥皮 / 刮铜 / 去蜡。
        if (!Config.INSTANCE.enableTreeFelling.get()) return;

        if (!(level instanceof ServerLevel)) return;
        if (!SawBlockEntity.isSawable(brokenState)) return;

        if (pos.distSqr(player.blockPosition()) > 64L * 64L) return;

        TreeCutter.Tree tree = TreeCutter.findTree(level, pos, brokenState);
        if (tree != TreeCutter.NO_TREE) {
            tree.destroyBlocks(level, null, (dropPos, dropStack) -> {
                if (dropStack.isEmpty()) return;
                // ★ 交给原版掉落逻辑：位置=方块中心，无初始速度
                Block.popResource(level, dropPos, dropStack);
            });
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }


    // ================= 去皮 =================

    private static boolean canStrip(BlockState state, UseOnContext context) {
        return state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false) != null;
    }

    private static void stripBlock(Level level, Player player, ItemStack saw,
                                   BlockPos pos, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);

        UseOnContext ctx = new UseOnContext(level, player, hand, saw,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

        BlockState stripped = state.getToolModifiedState(ctx, ItemAbilities.AXE_STRIP, false);
        if (stripped == null) return;

        level.setBlock(pos, stripped, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, stripped));
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
        saw.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
    }

    // ================= 切换配方 =================

    private static InteractionResult switchRecipe(Level level, Player player, ItemStack saw, ItemStack off) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        List<RecipeHolder<? extends Recipe<?>>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) {
            return InteractionResult.SUCCESS;   // ★ 删掉了原文文字提示
        }

        int current = saw.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX.get(), 0);
        int next = (current + 1) % recipes.size();
        saw.set(ModDataComponents.HAND_SAW_RECIPE_INDEX.get(), next);

        // ★ 删掉了"已选择：xxx（N/M）"的文字提示，只保留音效
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
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
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private Optional<BlockState> evaluateAxeAction(Level level, BlockPos pos,
                                                   @Nullable Player player, BlockState state,
                                                   UseOnContext context) {
        Optional<BlockState> stripped = Optional.ofNullable(
                state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false));
        if (stripped.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
            return stripped;
        }

        Optional<BlockState> scraped = Optional.ofNullable(
                state.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, false));
        if (scraped.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.levelEvent(player, 3005, pos, 0);
            return scraped;
        }

        Optional<BlockState> waxedOff = Optional.ofNullable(
                state.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, false));
        if (waxedOff.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.levelEvent(player, 3004, pos, 0);
            return waxedOff;
        }

        return Optional.empty();
    }

}
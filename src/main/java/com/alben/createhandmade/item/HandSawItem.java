package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.network.HandSawRecipesPacket;
import com.alben.createhandmade.recipe.HandMadeCuttingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.content.kinetics.saw.CuttingRecipe;
import com.zurrtum.create.content.kinetics.saw.SawBlockEntity;
import com.zurrtum.create.content.kinetics.saw.TreeCutter;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 手锯：连续切削（副手供料）、潜行整树砍伐、去皮 / 刮铜 / 去蜡。
 *
 * <p>Fabric 侧事件替代：</p>
 * <ul>
 *   <li>{@code PlayerInteractEvent.LeftClickBlock} → {@code AttackBlockCallback}（记录待砍目标）</li>
 *   <li>{@code BlockEvent.BreakEvent} → {@code PlayerBlockBreakEvents.AFTER}（触发整树砍伐）</li>
 *   <li>{@code ServerTickEvent.Post} → {@code ServerTickEvents.END_SERVER_TICK}（砍树循环音效）</li>
 *   <li>{@code AttackEntityEvent} 扣耐久 → 改用 {@code DataComponents.WEAPON}（见 ModItems）</li>
 * </ul>
 */
public class HandSawItem extends Item {

    /** 砍树循环音效间隔（tick） */
    private static final int FELL_SOUND_INTERVAL = 4;

    /**
     * 「正在切削」的玩家。
     *
     * <p>★ <b>必须按侧分开</b>：单人游戏里客户端与服务端同 JVM、同一个 UUID，共用一张表会让
     * 两侧互相 add/remove（一侧松手就把另一侧的结算也停掉）。这条教训来自
     * {@link StickyUseSession} 的 b2.6 回归 —— 那里的表现是"长按原地抽搐"，
     * 这里会表现成"偶尔少切一份"。</p>
     */
    private static final Set<UUID> CLIENT_CUTTING = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final Set<UUID> SERVER_CUTTING = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final Map<UUID, BlockPos> FELL_PENDING = new HashMap<>();

    /** 上一次推给该玩家的 HUD 数据对应的副手物品（数量恒 1；EMPTY = 已推过"没有可切的东西"）。 */
    private static final Map<UUID, ItemStack> LAST_PUSHED = new HashMap<>();

    public HandSawItem(Properties properties) {
        super(properties);
    }

    /** 该侧「正在切削」的玩家集合。 */
    private static Set<UUID> cuttingSet(Level level) {
        return level.isClientSide() ? CLIENT_CUTTING : SERVER_CUTTING;
    }

    // ================= 使用动画 =================

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // ★ 连续切削：使用时长恒为"无限"（72000），真正的结算周期由 onUseTick 按「起手 + 加工」控制。
        //   必须是常量：LivingEntity.getTicksUsingItem() = getUseDuration() - useItemRemaining。
        return 72000;
    }

    // ================= 左键 START：潜行 + 可锯方块 → 记录候选目标 =================

    /**
     * Fabric {@code AttackBlockCallback} 入口。返回 PASS 让原版继续挖掘。
     */
    public static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos,
                                                  Direction direction) {
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        // ★ 配置开关：关闭时完全不进入「待砍」状态 —— 不记录候选、不播锯木音效，
        //   潜行左键原木等同普通挖掘。与 fellTreeFromBroken 里的判定保持一致。
        if (!ServerConfig.INSTANCE.enableTreeFelling.get()) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(pos);
        if (!SawBlockEntity.isSawable(state)) {
            return InteractionResult.PASS;
        }

        FELL_PENDING.put(player.getUUID(), pos.immutable());
        return InteractionResult.PASS;
    }

    // ================= 服务端 tick：HUD 数据同步 + 砍树循环音效 =================

    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        syncHudData(server);
        tickFellSounds(server);
    }

    /**
     * 把手锯 HUD 需要的「副手物品能切出什么」推给客户端。
     *
     * <p>只在（主手手锯 + 副手物品）的<b>组合</b>变化时推送一次 —— 每 tick 都推会把网络打满，
     * 而配方本身只取决于副手物品的种类与组件（数量变化不影响）。副手清空/换物品时推一份
     * 空数据过去，客户端据此清掉 HUD 并让 {@code use()} 回到 PASS。</p>
     */
    private static void syncHudData(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack off = player.getOffhandItem();
            boolean relevant = player.getMainHandItem().getItem() instanceof HandSawItem && !off.isEmpty();
            ItemStack signature = relevant ? off.copyWithCount(1) : ItemStack.EMPTY;

            if (LAST_PUSHED.containsKey(player.getUUID())
                    && sameSignature(LAST_PUSHED.get(player.getUUID()), signature)) {
                continue;
            }
            LAST_PUSHED.put(player.getUUID(), signature);

            List<ItemStack> outputs = relevant
                    ? collectCuttingOutputs(player.level(), off)
                    : List.of();
            ServerPlayNetworking.send(player, new HandSawRecipesPacket(signature, outputs));
        }
    }

    /** 两个"副手签名"是否等价（两边都空 = 等价）。 */
    private static boolean sameSignature(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /** 玩家下线时清掉同步缓存（避免 UUID 复用/内存残留）。 */
    public static void clearPlayer(UUID uuid) {
        LAST_PUSHED.remove(uuid);
        FELL_PENDING.remove(uuid);
        CLIENT_CUTTING.remove(uuid);
        SERVER_CUTTING.remove(uuid);
    }

    private static void tickFellSounds(net.minecraft.server.MinecraftServer server) {
        if (FELL_PENDING.isEmpty()) return;

        boolean playSound = (server.getTickCount() % FELL_SOUND_INTERVAL == 0);

        Iterator<Map.Entry<UUID, BlockPos>> it = FELL_PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
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
                    0.7f + player.level().getRandom().nextFloat() * 0.2f);
        }
    }

    // ================= 方块真正被挖碎时：判定并触发整树砍伐 =================

    /**
     * Fabric {@code PlayerBlockBreakEvents.AFTER} 入口（服务端）。
     *
     * <p>事件触发时方块已经被移除，所以可以直接找树，不必再延迟一个 tick。</p>
     */
    public static void onBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(player.getMainHandItem().getItem() instanceof HandSawItem)) return;
        if (!player.isShiftKeyDown()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!SawBlockEntity.isSawable(state)) return;

        BlockPos pending = FELL_PENDING.get(player.getUUID());
        if (pending == null || !pending.equals(pos)) return;

        FELL_PENDING.remove(player.getUUID());

        ItemStack saw = player.getMainHandItem();
        fellTreeFromBroken(serverLevel, player, saw, pos.immutable(), state);
    }

    // ================= 右键 =================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack saw = player.getItemInHand(hand);
        ItemStack off = player.getOffhandItem();

        if (player.isShiftKeyDown() && !off.isEmpty()) {
            if (!level.isClientSide()) {
                switchRecipe(level, player, saw, off);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (!off.isEmpty() && hasCuttingRecipes(level, off)) {
            cuttingSet(level).add(player.getUUID());
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack saw = context.getItemInHand();
        ItemStack off = player.getOffhandItem();

        if (player.isShiftKeyDown() && !off.isEmpty()) {
            return switchRecipe(level, player, saw, off);
        }

        if (!off.isEmpty() && hasCuttingRecipes(level, off)) {
            cuttingSet(level).add(player.getUUID());
            player.startUsingItem(context.getHand());
            return InteractionResult.CONSUME;
        }

        return handleAxeAction(context);
    }

    // ================= 切削 tick：服务端广播音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (!cuttingSet(level).contains(player.getUUID())) return;

        // ★ 耐久耗尽：原版 hurtAndBreak 会把锯子栈清空（count → 0），但 LivingEntity.updatingUsingItem()
        //   判定是否继续使用用的是 ItemStack.isSameItem()，所以使用状态不会自动结束 ——
        //   不在这里拦住就会"空手"继续结算（材料照扣、产物照出）。
        if (stack.isEmpty()) {
            player.stopUsingItem();
            cuttingSet(level).remove(player.getUUID());
            return;
        }

        int windupTicks = Math.max(0, ServerConfig.INSTANCE.handSawWindupTicks.get());
        int processingTicks = Math.max(1, ServerConfig.INSTANCE.handSawVibrateTicks.get());

        // ★ 相位改用「会话加工 tick」= 会话 tick − 起手。续用时相位继续累加 ⇒
        //   结算周期从（起手 + 加工）变成纯加工，起手不会每份都算一遍。
        int cutTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windupTicks);
        if (cutTicks < 0) return;   // 还在起手期内：不出声、不结算

        // ★ 连续切削：每「加工时长」tick 结算一份
        boolean settlement = cutTicks > 0 && cutTicks % processingTicks == 0;
        if (settlement) {
            if (!level.isClientSide()) {
                executeCut(level, player, stack);
                // ★ 完成音"保底"：本件窗口 = [cutTicks - P, cutTicks - 1]；
                //   左端点若 > 0，它就是上一次的结算 tick（已让位、没播周期音）⇒ 从窗口里去掉。
                int windowLo = cutTicks - processingTicks;
                boolean cycleSoundPlayed = CycleSoundWindow.hasCycleTick(
                        windowLo > 0 ? windowLo + 1 : windowLo, cutTicks - 1, 4, 0);
                if (!cycleSoundPlayed) {
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                            SoundSource.PLAYERS, 0.8f, 1.4f + level.getRandom().nextFloat() * 0.2f);
                }
            }
            // 切完这一份后，副手没料 / 已无配方 → 两端停止使用
            ItemStack offAfterCut = player.getOffhandItem();
            if (offAfterCut.isEmpty() || !hasCuttingRecipes(level, offAfterCut)) {
                player.stopUsingItem();
                cuttingSet(level).remove(player.getUUID());
                return;
            }
        }

        // 音效/粒子保持 4t 的锯木节奏；相位同样取自会话 ⇒ 跨续用连续
        if (cutTicks % 4 != 0) return;

        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        // ★ 服务端：音效 + 粒子广播给附近玩家
        if (!level.isClientSide()) {
            // ★ 让位：结算 tick 的这一声已由上面的完成音接管（粒子照旧）⇒ 避免同 tick 双响
            if (!settlement) {
                level.playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                        SoundSource.PLAYERS, 0.8f,
                        1.4f + level.getRandom().nextFloat() * 0.2f);
            }

            if (level instanceof ServerLevel serverLevel) {
                Vec3 eye = entity.getEyePosition(1f);
                Vec3 look = entity.getLookAngle();
                Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2;
                    double speed = 0.05 + level.getRandom().nextDouble() * 0.1;

                    serverLevel.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(off)),
                            spawnPos.x, spawnPos.y, spawnPos.z,
                            1,
                            Math.cos(angle) * speed,
                            0.15 + level.getRandom().nextDouble() * 0.15,
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
        if (!(entity instanceof Player player)) return stack;
        cuttingSet(level).remove(player.getUUID());
        return stack;
    }

    // ================= releaseUsing：切削取消 =================

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        cuttingSet(level).remove(player.getUUID());
        return false;
    }

    // ================= 切削执行 =================

    private static void executeCut(Level level, Player player, ItemStack saw) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        List<RecipeHolder<? extends Recipe<?>>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return;

        int index = saw.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX, 0);
        if (index >= recipes.size()) index = 0;

        Recipe<?> recipe = recipes.get(index).value();
        SingleRecipeInput input = new SingleRecipeInput(off.copyWithCount(1));

        List<ItemStack> results = new ArrayList<>();
        // L3 独占配方（切削家族）与 Create 的 CuttingRecipe 都是滚动式配方，支持多产物 / 概率产物；
        // 切石（原版 StonecutterRecipe）是单产物。
        if (recipe instanceof CreateSingleStackRollableRecipe rollable) {
            results.addAll(rollable.assemble(input, level.getRandom()));
        } else if (recipe instanceof SingleItemRecipe stonecutting) {
            results.add(stonecutting.assemble(input));
        }

        // ★ 容器残留。必须在 off.shrink(1) 之前取。
        UseRemainder remainder = off.get(DataComponents.USE_REMAINDER);
        ItemStack craftingRemainder = remainder != null ? remainder.convertInto().create() : ItemStack.EMPTY;

        off.shrink(1);

        if (!craftingRemainder.isEmpty()) {
            results.add(craftingRemainder);
        }

        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            if (!player.getInventory().add(result.copy())) {
                player.drop(result.copy(), false);
            }
        }

        saw.hurtAndBreak(1, player, player.getUsedItemHand());

        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /**
     * 「这件副手物品现在能不能切」—— {@code use()} / {@code onUseTick} 的唯一判定入口。
     *
     * <p>★ 两侧判定方式不同，这是 26.1 的硬限制：</p>
     * <ul>
     *   <li><b>服务端</b>：直接查配方表（权威）；</li>
     *   <li><b>客户端</b>：没有配方表，只能读服务端推来的 {@link HandSawRecipeCache}。
     *       如果缓存里还没有这件物品的数据（刚把物品放进副手、还没收到包），<b>先乐观放行</b> ——
     *       真正结算在服务端，客户端多播一次动画远好于吞掉一次右键。</li>
     * </ul>
     */
    public static boolean hasCuttingRecipes(Level level, ItemStack off) {
        if (off.isEmpty()) return false;
        if (!level.isClientSide()) {
            return !getCuttingRecipes(level, off).isEmpty();
        }
        if (HandSawRecipeCache.matches(off)) {
            return !HandSawRecipeCache.outputs().isEmpty();
        }
        return true;
    }

    /**
     * 收集 HUD 要显示的「每种配方一个代表产物」。
     *
     * <p>1.21.1 用 {@code Recipe#getResultItem()}；26.1 已经删掉这个方法（配方只剩
     * {@code assemble} 与 {@code display}），所以这里改读滚动式配方<b>声明的</b>产物表
     * （{@link CreateSingleStackRollableRecipe#results()}，{@code ProcessingOutput#create()} 不掷概率，
     * 预览稳定），切石则用 {@code assemble} 的单产物。</p>
     */
    private static List<ItemStack> collectCuttingOutputs(Level level, ItemStack off) {
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<? extends Recipe<?>> holder : getCuttingRecipes(level, off)) {
            ItemStack preview = previewResult(holder.value(), off);
            if (!preview.isEmpty()) {
                outputs.add(preview);
            }
        }
        return outputs;
    }

    private static ItemStack previewResult(Recipe<?> recipe, ItemStack input) {
        if (recipe instanceof CreateSingleStackRollableRecipe rollable) {
            List<ProcessingOutput> declared = rollable.results();
            return declared.isEmpty() ? ItemStack.EMPTY : declared.get(0).create();
        }
        if (recipe instanceof SingleItemRecipe stonecutting) {
            return stonecutting.assemble(new SingleRecipeInput(input.copyWithCount(1)));
        }
        return ItemStack.EMPTY;
    }

    /**
     * 收集副手物品的所有可用切削配方：先锯木（切削），全不命中才退到切石。
     */
    public static List<RecipeHolder<? extends Recipe<?>>> getCuttingRecipes(Level level, ItemStack input) {
        if (input.isEmpty()) return List.of();

        List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.HAND_SAW, level)) {
            // ★ automation 过滤保留在调用方：配方池与 JEI 都不过滤这些"仅手动"配方。
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;

            // L3 独占配方（切削家族）：只认归属手锯的那些
            if (holder.value() instanceof HandMadeCuttingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.HAND_SAW) continue;
                if (!exclusive.matches(recipeInput, level)) continue;
                result.add(holder);
                continue;
            }

            if (!(holder.value() instanceof CuttingRecipe cuttingRecipe)) continue;
            if (!cuttingRecipe.matches(recipeInput, level)) continue;
            result.add(holder);
        }

        // ★ 锯木段（序列组装 + 切削）**全部不命中**时，才退到切石段。
        //   门控（跟随 Create 的 allowStonecuttingOnSaw）在配方池里做。
        if (result.isEmpty()) {
            for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(
                    HandMadeTool.HAND_SAW_STONECUTTING, level)) {
                if (!(holder.value() instanceof SingleItemRecipe stonecutting)) continue;
                if (!stonecutting.matches(recipeInput, level)) continue;
                result.add(holder);
            }
        }

        return result;
    }

    // ================= 整树砍伐 =================

    private static void fellTreeFromBroken(Level level, Player player, ItemStack saw,
                                           BlockPos pos, BlockState brokenState) {
        // ★ 配置开关：关闭时只破坏当前方块（原版行为）。
        if (!ServerConfig.INSTANCE.enableTreeFelling.get()) return;

        if (!(level instanceof ServerLevel)) return;
        if (!SawBlockEntity.isSawable(brokenState)) return;

        if (pos.distSqr(player.blockPosition()) > 64L * 64L) return;

        TreeCutter.Tree tree = TreeCutter.findTree(level, pos, brokenState);
        if (tree != TreeCutter.NO_TREE) {
            // 26.1 的 destroyBlocks 会顺带按方块概率给 toDamage 扣耐久
            tree.destroyBlocks(level, saw, player, (dropPos, dropStack) -> {
                if (dropStack.isEmpty()) return;
                Block.popResource(level, dropPos, dropStack);
            });
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    // ================= 切换配方 =================

    private static InteractionResult switchRecipe(Level level, Player player, ItemStack saw, ItemStack off) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        List<RecipeHolder<? extends Recipe<?>>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) {
            return InteractionResult.SUCCESS;
        }

        int current = saw.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX, 0);
        int next = (current + 1) % recipes.size();
        saw.set(ModDataComponents.HAND_SAW_RECIPE_INDEX, next);

        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.PLAYERS, 0.5f, 1.4f);

        return InteractionResult.SUCCESS_SERVER;
    }

    // ================= 斧头兜底：去皮 / 刮铜 / 去蜡 =================

    /**
     * 26.1 已无 NeoForge 的 {@code ItemAbilities} / {@code BlockState#getToolModifiedState}，
     * 而原版斧头逻辑收在 {@code AxeItem} 内部。这里直接委托给一把虚拟铁斧执行，
     * 再按结果给手锯扣耐久 —— 三套动作（去皮 / 刮铜 / 去蜡）的音效、粒子、门控全部与原版一致。
     */
    private static InteractionResult handleAxeAction(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack fakeAxe = new ItemStack(Items.IRON_AXE);
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        UseOnContext axeContext = new UseOnContext(player, context.getHand(), hit) {
            @Override
            public ItemStack getItemInHand() {
                return fakeAxe;
            }
        };

        InteractionResult result = Items.IRON_AXE.useOn(axeContext);
        if (!result.consumesAction()) return InteractionResult.PASS;

        if (!level.isClientSide()) {
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }
}

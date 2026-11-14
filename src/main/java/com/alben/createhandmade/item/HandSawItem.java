package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.config.SyncedServerConfig;
import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.enchantment.BatchCrafting;
import com.alben.createhandmade.item.renderer.HandSawItemRenderer;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeRecipes;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class HandSawItem extends Item {

    private static final int FELL_SOUND_INTERVAL = 4;

    public static final String NBT_RECIPE_INDEX = "HandSawRecipeIndex";
    /** ★ 当前选中配方的 ID。HUD 和切削都按 ID 查找，不依赖列表顺序。 */
    public static final String NBT_RECIPE_ID = "HandSawRecipeId";

    /**
     * 正在连续切削的玩家。
     *
     * <p>★ 客户端与服务端**各存一份**：{@code use()} / {@code useOn()} 与
     * {@code onUseTick()} 两侧都会跑，早期共用一个静态 HashSet 时两个线程会同时
     * add/remove 同一个表（HashSet 底层是 HashMap，扩容期可能死循环；或者一侧
     * remove 掉另一侧正在用的条目，表现为切削音效/粒子提前断掉）。</p>
     */
    private static final Set<UUID> CUTTING_PLAYERS_CLIENT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CUTTING_PLAYERS_SERVER = ConcurrentHashMap.newKeySet();

    private static Set<UUID> cuttingPlayers(Level level) {
        return level.isClientSide ? CUTTING_PLAYERS_CLIENT : CUTTING_PLAYERS_SERVER;
    }

    /** 整树砍伐的待处理目标。只在服务端写（{@code onLeftClickBlock} 里有 isClientSide 守卫）。 */
    private static final Map<UUID, BlockPos> FELL_PENDING = new HashMap<>();

    // ================= 配方 ID 反查 =================

    /**
     * 反查配方 ID。找不到（例如 SequencedAssemblyRecipe 的动态配方）返回 null，调用方回退到索引。
     *
     * <p>以前这里自带一份 {@code IdentityHashMap} 缓存，只在 RecipeManager <b>实例</b>变化时清空；
     * 客户端 {@code /reload} 走的是 {@code replaceRecipes}——实例不变、内容全换，
     * 缓存因此会一直返回重载前的旧 ID。现在统一委托给 {@link HandMadeRecipePool#recipeIdOf}，
     * 由它在资源重载时一并失效。</p>
     */
    @Nullable
    public static ResourceLocation getRecipeId(Level level, Recipe<?> recipe) {
        return HandMadeRecipePool.recipeIdOf(level, recipe);
    }

    public HandSawItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level,
                              net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide) return;
        if (level.getGameTime() % 100 != 0) return;

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) return;
        if (!tag.contains(NBT_RECIPE_INDEX)) return;
        if (tag.getInt(NBT_RECIPE_INDEX) != 0) return;

        tag.remove(NBT_RECIPE_INDEX);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ★ 允许锋利附魔
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.SHARPNESS) return true;
        return super.canApplyAtEnchantingTable(stack, enchantment);
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

    // ================= 使用动画 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
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

    // ================= 玩家离开：清掉按 UUID 记的运行期状态 =================

    /**
     * 断线/退服时清掉 {@link #CUTTING_PLAYERS_SERVER} 与 {@link #FELL_PENDING} 里属于该玩家的条目。
     *
     * <p>两个表都按 UUID 记录，正常情况下退出使用 / 松开按键会自己移除，
     * 但玩家在「正在连续切削」或「正在潜行砍树」的状态下直接断线时不会走到那些清理路径，
     * 残留条目会一直占着内存（UUID → 数据），并在同一 UUID 下次登录时表现为
     * 「一进服就在单手锯贴图」的假状态。</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUUID();
        CUTTING_PLAYERS_SERVER.remove(id);
        FELL_PENDING.remove(id);
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
            if (!Config.INSTANCE.enableTreeFelling.get()) return;
            if (!player.isShiftKeyDown()) return;

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
            cuttingPlayers(level).add(player.getUUID());
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
            cuttingPlayers(level).add(player.getUUID());
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

    // ================= 切削 tick：连续切削 + 音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        if (!cuttingPlayers(level).contains(player.getUUID())) return;

        if (stack.isEmpty()) {
            player.stopUsingItem();
            cuttingPlayers(level).remove(player.getUUID());
            return;
        }

        // ★ 两侧都跑：客户端用服务端同步下来的切削节奏，音效/粒子才不会抢先或迟到
        int windupTicks = SyncedServerConfig.handSawWindup();
        int cutTicks = Math.max(1, SyncedServerConfig.handSawCut());

        int usedTicks = getUseDuration(stack) - remainingTicks;

        // ★ 第一次含起手：windupTicks + cutTicks
        //   后续只算加工：每 cutTicks 一次，不再重复起手
        if (usedTicks >= windupTicks + cutTicks
                && (usedTicks - windupTicks) % cutTicks == 0) {
            if (!level.isClientSide) {
                executeCut(level, player, stack, null);
            }
            ItemStack offAfterCut = player.getOffhandItem();
            if (offAfterCut.isEmpty() || getCuttingRecipes(level, offAfterCut).isEmpty()) {
                player.stopUsingItem();
                cuttingPlayers(level).remove(player.getUUID());
                return;
            }
        }

        if (usedTicks < windupTicks) return;
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

    // ================= finishUsingItem：仅清理状态 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        cuttingPlayers(level).remove(player.getUUID());
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        cuttingPlayers(level).remove(player.getUUID());
    }

    // ================= 切削执行 =================

    /**
     * 切削执行（支持批量加工附魔）。
     *
     * 批量逻辑：
     *   - 读手锯上的 Batch Crafting 等级 → 1/2/4/8 份
     *   - 受副手物品数量限制
     *   - 一次消耗 N 份原料，产物 × N
     *   - 耐久只扣 1 点（批量是附魔收益）
     *
     * 配方选择：优先按 NBT 里的配方 ID 查找，找不到再回退到索引。
     */
    public static boolean executeCut(Level level, LivingEntity entity, ItemStack saw,
                                     @Nullable FilterItemStack filter) {
        if (saw.isEmpty()) return false;

        ItemStack off = entity.getOffhandItem();
        if (off.isEmpty()) return false;

        List<Recipe<?>> recipes = getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return false;

        // ★ 批量：读附魔等级，受副手数量限制
        int batchSize = BatchCrafting.getBatchSize(saw);
        int actual = BatchCrafting.clampToAvailable(batchSize, off.getCount());
        if (actual <= 0) return false;

        // ---- 选配方（一次选，应用 N 次） ----
        Recipe<?> recipe = null;
        List<ItemStack> singleResults = new ArrayList<>();

        if (filter != null) {
            for (Recipe<?> candidate : recipes) {
                List<ItemStack> candidateResults = new ArrayList<>();
                if (candidate instanceof CuttingRecipe cr) {
                    candidateResults = cr.rollResults();
                } else if (candidate instanceof ProcessingRecipe<?> pr) {
                    candidateResults = pr.rollResults();
                } else {
                    candidateResults.add(candidate.getResultItem(level.registryAccess()).copy());
                }
                if (resultsPassFilter(level, candidateResults, filter)) {
                    recipe = candidate;
                    singleResults = candidateResults;
                    break;
                }
            }
            if (recipe == null) return false;
        } else {
            // ★ 优先按配方 ID 查找
            String idStr = saw.getOrCreateTag().getString(NBT_RECIPE_ID);
            if (!idStr.isEmpty()) {
                ResourceLocation targetId = ResourceLocation.tryParse(idStr);
                if (targetId != null) {
                    for (Recipe<?> candidate : recipes) {
                        ResourceLocation cid = getRecipeId(level, candidate);
                        if (targetId.equals(cid)) {
                            recipe = candidate;
                            break;
                        }
                    }
                }
            }

            // 找不到（旧 NBT 或动态配方）→ 回退到索引
            if (recipe == null) {
                int index = saw.getOrCreateTag().getInt(NBT_RECIPE_INDEX);
                if (index < 0 || index >= recipes.size()) index = 0;
                recipe = recipes.get(index);
            }

            if (recipe instanceof CuttingRecipe cr) {
                singleResults = cr.rollResults();
            } else if (recipe instanceof ProcessingRecipe<?> pr) {
                singleResults = pr.rollResults();
            } else {
                singleResults.add(recipe.getResultItem(level.registryAccess()).copy());
            }
        }

        // ★ 一次消耗 actual 份原料
        off.shrink(actual);

        // ★ 产物 × actual，按 maxStackSize 拆分发放
        for (ItemStack single : singleResults) {
            if (single.isEmpty()) continue;
            int total = single.getCount() * actual;
            int max = single.getMaxStackSize();
            while (total > 0) {
                int give = Math.min(total, max);
                ItemStack copy = single.copy();
                copy.setCount(give);
                if (entity instanceof Player player) {
                    if (!player.getInventory().add(copy)) {
                        player.drop(copy, false);
                    }
                } else {
                    entity.spawnAtLocation(copy);
                }
                total -= give;
            }
        }

        // ★ 耐久：批量只扣 1 点
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

    // ================= 切削配方缓存 =================
    // ★ getCuttingRecipes 一次调用要遍历全部 CUTTING 配方逐个 matches（整合包里常有几百条），
    //   还要扫一遍 TOOL_RECIPE，并分配 2~3 套 ItemStackHandler/RecipeWrapper + 列表。
    //   而它的调用点很密：
    //     - use() / useOn()            每次右键
    //     - onUseTick()                每个切削周期
    //     - 女仆手锯行为                每冷却窗口一次
    //     - 传送带 handleProcessingOnAllItems   **每个物品各一次**
    //   所以这里按「输入物品」做一层结果缓存。
    //
    //   ★ 键里带上 RecipeManager 身份，而不是"manager 变了就清表"：单人游戏里客户端线程
    //     和服务端线程各自持有**不同的** RecipeManager 实例，两个线程交替调用时会互相
    //     把对方刚算好的缓存清掉，等于缓存完全失效。做成键的一部分后两边各存各的。
    //
    //   失效：HandMadeRecipePool.cacheEpoch()（过滤器增删、资源/脚本重载都会 +1）变化时整表清空。
    //   键的 ItemStack 用 copyWithCount(1)，其 equals/hashCode 比的是 item + NBT；
    //   键一旦入表不再改动。表本身是 ConcurrentHashMap，双线程访问安全。

    private static final int CUTTING_CACHE_MAX = 512;
    private static final Map<CuttingKey, List<Recipe<?>>> CUTTING_CACHE = new ConcurrentHashMap<>();
    private static final Object CUTTING_CACHE_EPOCH_LOCK = new Object();
    private static volatile int cuttingCacheEpoch = Integer.MIN_VALUE;

    /** 缓存键：同一份配方表（manager）+ 同一版本号 + 同一件输入物品。 */
    private record CuttingKey(RecipeManager manager, int epoch, ItemStack input) {}

    public static List<Recipe<?>> getCuttingRecipes(Level level, ItemStack input) {
        if (level == null || input == null || input.isEmpty()) return List.of();

        RecipeManager manager = level.getRecipeManager();
        int epoch = HandMadeRecipePool.cacheEpoch();

        if (epoch != cuttingCacheEpoch) {
            synchronized (CUTTING_CACHE_EPOCH_LOCK) {
                if (epoch != cuttingCacheEpoch) {
                    CUTTING_CACHE.clear();
                    cuttingCacheEpoch = epoch;
                }
            }
        }

        CuttingKey key = new CuttingKey(manager, epoch, input.copyWithCount(1));
        List<Recipe<?>> cached = CUTTING_CACHE.get(key);
        if (cached != null) return cached;

        // ★ 不用 List.copyOf：它对 null 元素会抛 NPE。这里要的是"不可变 + 允许 null"。
        List<Recipe<?>> computed =
                Collections.unmodifiableList(new ArrayList<>(computeCuttingRecipes(level, key.input())));

        // 简单限长：条目到顶就整体清空，不做 LRU（物品种类本来就不该无上限增长）
        if (CUTTING_CACHE.size() >= CUTTING_CACHE_MAX) {
            CUTTING_CACHE.clear();
        }

        List<Recipe<?>> race = CUTTING_CACHE.putIfAbsent(key, computed);
        return race != null ? race : computed;
    }

    /** 单格输入的配方 wrapper；L3 查询按需创建用（绝大多数整合包没有 L3 配方，先建就是白分配）。 */
    private static RecipeWrapper wrapSingle(ItemStack input) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        return new RecipeWrapper(handler);
    }

    /** 真正干活的部分：不缓存，只由 {@link #getCuttingRecipes} 在未命中时调用。 */
    private static List<Recipe<?>> computeCuttingRecipes(Level level, ItemStack input) {
    if (input.isEmpty()) return List.of();

        // ---- 段 1：L3 独占切削（独立 type） ----
        List<Recipe<?>> custom = new ArrayList<>();
        // ★ 懒创建：只有真的遇到 tool=hand_saw 的 L3 配方才需要 wrapper
        RecipeWrapper customWrapper = null;

        for (ProcessingRecipe<RecipeWrapper> recipe : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(recipe instanceof HandMadeToolRecipeLike like)) continue;
            if (like.getTool() != ToolType.HAND_SAW) continue;
            if (!HandMadeRecipePool.isAvailable(ToolType.HAND_SAW, level, recipe)) continue;
            if (customWrapper == null) customWrapper = wrapSingle(input);
            if (!recipe.matches(customWrapper, level)) continue;
            custom.add(recipe);
        }
        if (!custom.isEmpty()) return sortById(level, custom);

        // ---- 段 2：L1 序列组装 + 切削 ----
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

        if (!result.isEmpty()) {
            return sortById(level, HandMadeRecipePool.applyFilter(ToolType.HAND_SAW, level, result));
        }

        // ---- 段 3：切石回退 ----
        List<Recipe<?>> stonecutting = getStonecuttingRecipes(level, input);
        return sortById(level, HandMadeRecipePool.applyFilter(
                ToolType.HAND_SAW_STONECUTTING, level, stonecutting));
    }

    /**
     * 按配方 ID 排序，保证客户端和服务端的列表顺序一致。
    * 动态配方（ID 为 null）排在最后。
    */
    private static List<Recipe<?>> sortById(Level level, List<Recipe<?>> recipes) {
        if (recipes.size() <= 1) return recipes;

        // ★ 装饰-排序：ID 先一次性取好再排序。原实现把 getRecipeId 放进比较器，
        //   排序过程要回调 O(n log n) 次，每次都是一次 map 查找；现在只取 n 次。
        record Keyed(Recipe<?> recipe, @Nullable ResourceLocation id) {}

        List<Keyed> keyed = new ArrayList<>(recipes.size());
        for (Recipe<?> recipe : recipes) {
            keyed.add(new Keyed(recipe, getRecipeId(level, recipe)));
        }

        // null（动态配方）排最后
        keyed.sort((a, b) -> {
            if (a.id() == null && b.id() == null) return 0;
            if (a.id() == null) return 1;
            if (b.id() == null) return -1;
            return a.id().compareTo(b.id());
        });

        List<Recipe<?>> sorted = new ArrayList<>(keyed.size());
        for (Keyed k : keyed) {
            sorted.add(k.recipe());
        }
        return sorted;
    }

    private static List<Recipe<?>> getStonecuttingRecipes(Level level, ItemStack input) {
        if (!allowsStonecutting()) return List.of();

        List<Recipe<?>> result = new ArrayList<>();

        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, input.copyWithCount(1));

        for (StonecutterRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(RecipeType.STONECUTTING)) {
            if (recipe.matches(container, level)) {
                result.add(recipe);
            }
        }

        for (HandMadeRecipes.Stonecutting recipe : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.STONECUTTING_RECIPE.get())) {
            if (recipe.matches(container, level)) {
                result.add(recipe);
            }
        }

        return result;
    }

    private static boolean allowsStonecutting() {
        try {
            var server = com.simibubi.create.infrastructure.config.AllConfigs.server();
            if (server == null) return true;
            return server.recipes.allowStonecuttingOnSaw.get();
        } catch (Throwable t) {
            return true;
        }
    }

    // ================= 整树砍伐 =================

    private static void fellTreeFromBroken(Level level, Player player, ItemStack saw,
                                           BlockPos pos, BlockState brokenState) {
        if (!Config.INSTANCE.enableTreeFelling.get()) return;

        if (!(level instanceof ServerLevel)) return;
        if (!SawBlockEntity.isSawable(brokenState)) return;
        if (pos.distSqr(player.blockPosition()) > 64L * 64L) return;

        TreeCutter.Tree tree = TreeCutter.findTree(level, pos, brokenState);
        if (tree != TreeCutter.NO_TREE) {
            // ★ 传玩家 + 工具栈，不要传 null：
            //   - 玩家上下文让领地/保护插件、统计、进度、BreakEvent 系监听能看到剩下的木头
            //   - 工具栈让 Create 按正常规则算掉落（附魔、精准采集等）
            //   代价是耐久仍然只扣 1 点：TreeCutter.Tree 没有公开的方块数量接口，
            //   想做"按块计价"得自己重走一遍树的遍历，收益有限，先不动。
            tree.destroyBlocks(level, saw, player, (dropPos, dropStack) -> {
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
        if (next == 0) {
    saw.removeTagKey(NBT_RECIPE_INDEX);
} else {
    saw.getOrCreateTag().putInt(NBT_RECIPE_INDEX, next);
}

        // ★ 同时存配方 ID，客户端按 ID 查找，不依赖列表顺序
        Recipe<?> nextRecipe = recipes.get(next);
        ResourceLocation nextId = getRecipeId(level, nextRecipe);
        if (nextId != null) {
            saw.getOrCreateTag().putString(NBT_RECIPE_ID, nextId.toString());
        } else {
            // 动态配方（不在 RecipeManager 里）没有稳定 ID，清掉旧 ID 让执行时回退到索引
            saw.removeTagKey(NBT_RECIPE_ID);
        }

        if (player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }

        ItemStack result = nextRecipe.getResultItem(level.registryAccess());

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
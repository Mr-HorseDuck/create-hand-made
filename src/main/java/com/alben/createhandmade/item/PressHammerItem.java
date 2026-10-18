package com.alben.createhandmade.item;

import com.alben.createhandmade.enchantment.BatchCraftingHelper;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeToolRecipe;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.Config;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber
public class PressHammerItem extends Item {

    int chargeTicks = Config.INSTANCE.pressHammerChargeTicks.get();
    private static final Object COMPACTING_RECIPE_KEY = new Object();
    int chargeWindowTicks = Config.INSTANCE.pressHammerChargeWindowTicks.get();

    private static final float CHARGED_CRIT_MULTIPLIER = 1.5F;

    private static final Map<UUID, Long> CHARGE_SWING = new HashMap<>();
    private static final Map<UUID, Long> CHARGE_TARGET = new HashMap<>();
    private static final Map<UUID, Long> CHARGED_ATTACK = new HashMap<>();

    public PressHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 拦截右键 =================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        boolean shouldIntercept = false;

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            shouldIntercept = !depot.getHeldItem().isEmpty();
        } else if (BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) != null) {
            shouldIntercept = true;
        } else if (level.getBlockState(pos).getBlock() instanceof BasinBlock
                && level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            shouldIntercept = !basin.isEmpty();
        }

        if (shouldIntercept) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    // ================= 蓄力 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    // ================= 蓄力刚满：提示音 =================

    /**
     * 蓄力刚满那一刻（usedTicks == CHARGE_TICKS）播一声提示音。
     *
     * <p>与风箱同一套做法、同一个音效 {@link AllSoundEvents#CONFIRM}（Create 自带的
     * "确认叮"，语义是"就绪"）。只在客户端播：这是给操作者本人的即时反馈，
     * 服务端 {@code playSound(null, …)} 会广播给附近所有人，蓄力提示音不该如此。</p>
     *
     * <p>用 {@code usedTicks == CHARGE_TICKS} 精确触发一次 —— 若写
     * {@code usedTicks % CHARGE_TICKS} 会每 15 tick 重复响。</p>
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
    if (!(entity instanceof Player player)) return;
    int usedTicks = getUseDuration(stack) - remainingTicks;
    int chargeTicks = Config.INSTANCE.pressHammerChargeTicks.get();
    if (usedTicks == chargeTicks && level.isClientSide) {
        player.playSound(AllSoundEvents.CONFIRM.getMainEvent(), 1.0f, 1.0f);
    }
}

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack) - timeLeft;
        if (usedTicks < Config.INSTANCE.bellowsChargeTicks.get()) return;
        if (level.isClientSide) return;

        long gameTime = level.getGameTime();
        CHARGE_SWING.put(player.getUUID(), gameTime);
        player.swing(player.getUsedItemHand(), true);

        double entityReach = player.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        EntityHitResult entityHit = findEntityHit(player, eye, look, entityReach);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            CHARGE_TARGET.put(target.getUUID(), gameTime);
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(
                    level, target.blockPosition(), 1f, 0.8f);
            CHARGED_ATTACK.put(player.getUUID(), gameTime);
            player.attack(target);
            stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }

        double blockReach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        Vec3 end = eye.add(look.scale(blockReach));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            List<ItemStack> particleItems = findTargetItems(level, pos);

            // ★ 批量：读附魔等级
            int batchSize = BatchCraftingHelper.getBatchSize(stack);

            tryPressBasin(level, pos, null);
            tryPressTransported(level, pos, null, batchSize);

            if (!particleItems.isEmpty()) {
                broadcastParticles(level, pos, particleItems);
            }
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, pos, 1f, 0.8f);

            stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        } else {
            Vec3 soundPos = eye.add(look.scale(1.0));
            level.playSound(null, BlockPos.containing(soundPos),
                    SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.6f, 0.9f);
            stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.SHARPNESS) return true;
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Nullable
    private static EntityHitResult findEntityHit(Player player, Vec3 eye, Vec3 look, double reach) {
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity,
                reach * reach);
    }

    // ================= 普通攻击扣耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) return;

        Long t = CHARGED_ATTACK.remove(player.getUUID());
        if (t != null && player.level().getGameTime() - t <= 2) return;

        player.getMainHandItem().hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        // 保留原逻辑，TOOL 组件自动扣耐久
    }

    // ================= 蓄力暴击 =================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Long targetTime = CHARGE_TARGET.get(target.getUUID());
        if (targetTime == null) return;

        long elapsed = target.level().getGameTime() - targetTime;
        if (elapsed > Config.INSTANCE.pressHammerChargeWindowTicks.get()) {
            CHARGE_TARGET.remove(target.getUUID());
            return;
        }

        if (event.getSource().getEntity() instanceof Player attacker) {
            if (isVanillaCritCondition(attacker)) {
                return;
            }
        }

        float originalDamage = event.getAmount();
        event.setAmount(originalDamage * CHARGED_CRIT_MULTIPLIER);
    }

    private static boolean isVanillaCritCondition(Player player) {
        return player.fallDistance > 0.0F
                && !player.onGround()
                && !player.isInWater()
                && !player.isPassenger()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isFallFlying();
    }

    // ================= 蓄力额外击退 =================

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        Long targetTime = CHARGE_TARGET.get(target.getUUID());
        if (targetTime == null) return;

        long elapsed = target.level().getGameTime() - targetTime;
        if (elapsed > Config.INSTANCE.pressHammerChargeWindowTicks.get()) {
            CHARGE_TARGET.remove(target.getUUID());
            return;
        }

        event.setStrength(event.getStrength() + 2.0F);
        CHARGE_TARGET.remove(target.getUUID());
    }

    // ================= 探测目标物品 =================

    private static List<ItemStack> findTargetItems(Level level, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            ItemStack s = depot.getHeldItem();
            if (!s.isEmpty()) result.add(s.copy());
            return result;
        }

        if (level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
                ItemStack s = basin.inputInventory.getItem(i);
                if (!s.isEmpty()) result.add(s.copy());
            }
            return result;
        }

        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler != null) {
            handler.handleProcessingOnAllItems(item -> {
                if (!item.stack.isEmpty()) result.add(item.stack.copy());
                return TransportedResult.doNothing();
            });
        }

        return result;
    }

    // ================= 广播粒子 =================

    private static void broadcastParticles(Level level, BlockPos pos, List<ItemStack> stacks) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (stacks.isEmpty()) return;

        PressParticlesPacket packet = new PressParticlesPacket(pos, stacks, ParticleStyle.PRESS);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    // ================= 工作盆（basin 家族） =================

    public static boolean tryPressBasin(Level level, BlockPos pos, @Nullable FilterItemStack filter) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return false;
        if (basin.isEmpty()) return false;

        // L3：独立 type + tool=press_hammer_basin
        for (Recipe<?> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipe htr)) continue;
            if (htr.getTool() != ToolType.PRESS_HAMMER_BASIN) continue;
            if (!BasinRecipe.match(basin, htr)) continue;
            if (!resultsPassFilter(level, htr, filter)) continue;
            if (BasinRecipe.apply(basin, htr)) {
                basin.notifyChangeOfContents();
                return true;
            }
        }

        // L1：RecipeFinder
        try {
            for (Recipe<?> recipe : RecipeFinder.get(COMPACTING_RECIPE_KEY, level,
                    PressHammerItem::matchStaticFilters)) {
                if (!BasinRecipe.match(basin, recipe)) continue;

                List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                        ToolType.PRESS_HAMMER_BASIN, level, List.of(recipe));
                if (filtered.isEmpty()) continue;

                if (!resultsPassFilter(level, recipe, filter)) continue;

                if (BasinRecipe.apply(basin, recipe)) {
                    basin.notifyChangeOfContents();
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean matchStaticFilters(Recipe<?> recipe) {
        boolean isCompressibleCrafting = recipe instanceof CraftingRecipe
                && !(recipe instanceof MechanicalCraftingRecipe)
                && canCompress(recipe)
                && !AllRecipeTypes.shouldIgnoreInAutomation(recipe);
        boolean isCompactingRecipe = recipe.getType() == AllRecipeTypes.COMPACTING.getType();
        return isCompressibleCrafting || isCompactingRecipe;
    }

    private static boolean canCompress(Recipe<?> recipe) {
        if (!(recipe instanceof CraftingRecipe)
                || !AllConfigs.server().recipes.allowShapedSquareInPress.get())
            return false;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        return (ingredients.size() == 4 || ingredients.size() == 9)
                && ItemHelper.matchAllIngredients(ingredients);
    }

    // ================= 置物台 / 传送带（depot 家族） =================

    @Nullable
    public static Recipe<?> findPressingRecipe(Level level, ItemStack stack) {
        if (stack.isEmpty()) return null;

        // ---- 段 1：L3 独立 type ----
        ItemStackHandler l3Handler = new ItemStackHandler(1);
        l3Handler.setStackInSlot(0, stack.copyWithCount(1));
        RecipeWrapper l3Wrapper = new RecipeWrapper(l3Handler);

        for (ProcessingRecipe<RecipeWrapper> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipeLike like)) continue;
            if (like.getTool() != ToolType.PRESS_HAMMER_DEPOT) continue;
            if (!r.matches(l3Wrapper, level)) continue;
            return r;
        }

        // ---- 段 2 + 3：L1 序列组装 / PRESSING ----
        Recipe<?> result = null;

        Optional<PressingRecipe> sequenced =
                SequencedAssemblyRecipe.getRecipe(level, stack,
                        AllRecipeTypes.PRESSING.getType(), PressingRecipe.class);
        if (sequenced.isPresent()) {
            result = sequenced.get();
        } else {
            ItemStackHandler handler = new ItemStackHandler(1);
            handler.setStackInSlot(0, stack.copyWithCount(1));
            RecipeWrapper wrapper = new RecipeWrapper(handler);

            result = AllRecipeTypes.PRESSING.find(wrapper, level)
                    .filter(AllRecipeTypes.CAN_BE_AUTOMATED)
                    .map(r -> (PressingRecipe) r)
                    .orElse(null);
        }

        if (result == null) return null;

        List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                ToolType.PRESS_HAMMER_DEPOT, level, List.of(result));
        return filtered.isEmpty() ? null : filtered.get(0);
    }

    /** 旧签名（女仆行为等调用方用），batchSize = 1。 */
    public static boolean tryPressTransported(Level level, BlockPos pos, @Nullable FilterItemStack filter) {
        return tryPressTransported(level, pos, filter, 1);
    }

    /**
     * 批量加工版本。
     *
     * @param batchSize 本次最多处理多少份。由调用方从工具栈上的 Batch Crafting 附魔读出。
     *                  处理 N 份 = 从物品堆叠里拆 N 个，逐个应用配方，产物累加。
     */
    public static boolean tryPressTransported(Level level, BlockPos pos,
                                              @Nullable FilterItemStack filter, int batchSize) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        if (batchSize < 1) batchSize = 1;

        int[] processed = {0};   // 已处理的份数（跨所有 item 累计）
        final int maxBatch = batchSize;

        handler.handleProcessingOnAllItems(item -> {
            if (processed[0] >= maxBatch) return TransportedResult.doNothing();

            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            Recipe<?> recipe = findPressingRecipe(level, stack);
            if (recipe == null) return TransportedResult.doNothing();

            // ★ 本次从这个堆叠里拆出多少份（受剩余批量和堆叠数量双重限制）
            int toProcess = Math.min(maxBatch - processed[0], stack.getCount());
            if (toProcess <= 0) return TransportedResult.doNothing();

            // ★ 循环 toProcess 次，每次处理 1 个，累加产物
            List<ItemStack> allResults = new ArrayList<>();
            for (int i = 0; i < toProcess; i++) {
                List<ItemStack> one = RecipeApplier.applyRecipeOn(
                        level, stack.copyWithCount(1), recipe, true);
                allResults.addAll(one);
            }

            if (!resultsPassFilter(level, allResults, filter)) {
                return TransportedResult.doNothing();
            }

            processed[0] += toProcess;

            // 堆叠剩余
            ItemStack left = stack.copy();
            left.shrink(toProcess);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : allResults) {
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
        });

        return processed[0] > 0;
    }

    // ================= 过滤器辅助 =================

    private static boolean resultsPassFilter(Level level, Recipe<?> recipe,
                                             @Nullable FilterItemStack filter) {
        if (filter == null) return true;

        if (recipe instanceof BasinRecipe br) {
            try {
                List<ItemStack> results = br.rollResults();
                for (ItemStack s : results) {
                    if (!s.isEmpty() && filter.test(level, s)) return true;
                }
                return false;
            } catch (Exception ignored) {
            }
        }

        ItemStack result = recipe.getResultItem(level.registryAccess());
        return !result.isEmpty() && filter.test(level, result);
    }

    private static boolean resultsPassFilter(Level level, List<ItemStack> results,
                                             @Nullable FilterItemStack filter) {
        if (filter == null) return true;
        for (ItemStack s : results) {
            if (!s.isEmpty() && filter.test(level, s)) return true;
        }
        return false;
    }
}
package com.alben.createhandmade.item;

import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
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

    private static final int CHARGE_TICKS = 15;
    private static final Object COMPACTING_RECIPE_KEY = new Object();
    private static final int CHARGE_WINDOW_TICKS = 20;

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

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack) - timeLeft;
        if (usedTicks < CHARGE_TICKS) return;
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

            // ★ 玩家手动触发时不用过滤器
            tryPressBasin(level, pos, null);
            tryPressTransported(level, pos, null);

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

    @Nullable
    private static EntityHitResult findEntityHit(Player player, Vec3 eye, Vec3 look, double reach) {
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity,
                reach * reach);
    }

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

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Long targetTime = CHARGE_TARGET.get(target.getUUID());
        if (targetTime == null) return;

        long elapsed = target.level().getGameTime() - targetTime;
        if (elapsed > CHARGE_WINDOW_TICKS) {
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

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        Long targetTime = CHARGE_TARGET.get(target.getUUID());
        if (targetTime == null) return;

        long elapsed = target.level().getGameTime() - targetTime;
        if (elapsed > CHARGE_WINDOW_TICKS) {
            CHARGE_TARGET.remove(target.getUUID());
            return;
        }

        event.setStrength(event.getStrength() + 2.0F);
        CHARGE_TARGET.remove(target.getUUID());
    }

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

    // ================= 过滤器辅助 =================

    /**
     * 检查配方产物是否通过过滤器。
     * filter 为 null 时直接返回 true（不过滤）。
     */
    private static boolean resultsPassFilter(Level level, Recipe<?> recipe,
                                             @Nullable FilterItemStack filter) {
        if (filter == null) return true;

        // 优先用 BasinRecipe 的 rollResults 获取真实产物
        if (recipe instanceof BasinRecipe br) {
            try {
                List<ItemStack> results = br.rollResults();
                for (ItemStack s : results) {
                    if (!s.isEmpty() && filter.test(level, s)) return true;
                }
                return false;
            } catch (Exception ignored) {
                // 退回到 getResultItem
            }
        }

        ItemStack result = recipe.getResultItem(level.registryAccess());
        return !result.isEmpty() && filter.test(level, result);
    }

    /**
     * 检查一组 ItemStack 是否通过过滤器。
     */
    private static boolean resultsPassFilter(Level level, List<ItemStack> results,
                                             @Nullable FilterItemStack filter) {
        if (filter == null) return true;
        for (ItemStack s : results) {
            if (!s.isEmpty() && filter.test(level, s)) return true;
        }
        return false;
    }

    // ================= 工作盆（三层配方） =================

    /**
     * ★ 女仆兼容：
     *   - public static，供 TLM 行为类调用
     *   - @Nullable FilterItemStack filter：过滤**产物**，null 表示不过滤
     */
    public static boolean tryPressBasin(Level level, BlockPos pos, @Nullable FilterItemStack filter) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return false;
        if (basin.isEmpty()) return false;

        // ★ L3：优先查独占配方
        List<Recipe<?>> custom = HandMadeRecipePool.getAllCustomRecipes(ToolType.PRESS_HAMMER);
        for (Recipe<?> r : custom) {
            if (!BasinRecipe.match(basin, r)) continue;

            // ★ 过滤产物
            if (!resultsPassFilter(level, r, filter)) continue;

            if (BasinRecipe.apply(basin, r)) {
                basin.notifyChangeOfContents();
                return true;
            }
        }

        // ★ L1：原有逻辑 + ★ L2 过滤
        try {
            for (Recipe<?> recipe : RecipeFinder.get(COMPACTING_RECIPE_KEY, level,
                    PressHammerItem::matchStaticFilters)) {
                if (!BasinRecipe.match(basin, recipe)) continue;

                // ★ L2：应用过滤，被过滤的跳过继续找下一个
                List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                        ToolType.PRESS_HAMMER, level, List.of(recipe));
                if (filtered.isEmpty()) continue;

                // ★ 过滤产物
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

    // ================= 置物台 / 传送带（三层配方） =================

    @Nullable
    private static Recipe<?> findPressingRecipe(Level level, ItemStack stack) {
        if (stack.isEmpty()) return null;

        List<Recipe<?>> custom = HandMadeRecipePool.getCustomRecipes(
                ToolType.PRESS_HAMMER, level, stack);
        for (Recipe<?> r : custom) {
            if (r instanceof PressingRecipe) {
                return r;
            }
        }

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
                ToolType.PRESS_HAMMER, level, List.of(result));
        return filtered.isEmpty() ? null : filtered.get(0);
    }

    /**
     * ★ 女仆兼容：
     *   - public static，供 TLM 行为类调用
     *   - @Nullable FilterItemStack filter：过滤**产物**，null 表示不过滤
     */
    public static boolean tryPressTransported(Level level, BlockPos pos,
                                              @Nullable FilterItemStack filter) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            Recipe<?> recipe = findPressingRecipe(level, stack);
            if (recipe == null) return TransportedResult.doNothing();

            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, stack.copyWithCount(1), recipe, true);

            // ★ 过滤产物
            if (!resultsPassFilter(level, results, filter)) {
                return TransportedResult.doNothing();
            }

            success[0] = true;

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
        });

        return success[0];
    }
}
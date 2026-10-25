package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.zurrtum.create.content.kinetics.millstone.MillingRecipe;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import com.zurrtum.create.foundation.recipe.TimedRecipe;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MortarItem extends Item {

    // ================= 时间参数（研钵 / 碾钵各一套，键见 ServerConfig） =================

    /** 加工时长上限（tick）：防止"极端系数 × 长配方"把一次加工拖到几分钟。 */
    public static final int MAX_PROCESSING_TICKS = 1200;

    /** 起手时长（tick）。{@code crusherMortar = true} 时读碾钵那一套键。 */
    public static int windupTicks(boolean crusherMortar) {
        return (crusherMortar ? ServerConfig.INSTANCE.crusherMortarWindupTicks
                : ServerConfig.INSTANCE.mortarWindupTicks).get();
    }

    /** 捶捣周期（tick），至少 1（避免除零）。 */
    public static int poundCycleTicks(boolean crusherMortar) {
        return Math.max(1, (crusherMortar ? ServerConfig.INSTANCE.crusherMortarPoundCycleTicks
                : ServerConfig.INSTANCE.mortarPoundCycleTicks).get());
    }

    /** 解析「单物品加工时长」= 配方 {@code processing_time} × 系数，<b>不含起手</b>（tick）。 */
    public static int resolveDuration(@Nullable RecipeHolder<?> recipe, boolean crusherMortar) {
        int base = recipe != null && recipe.value() instanceof TimedRecipe timed ? timed.time() : 0;
        if (base <= 0) {
            base = Math.max(1, (crusherMortar ? ServerConfig.INSTANCE.crusherMortarDefaultDuration
                    : ServerConfig.INSTANCE.mortarDefaultDuration).get());
        }
        double multiplier = (crusherMortar ? ServerConfig.INSTANCE.crusherMortarDurationMultiplier
                : ServerConfig.INSTANCE.mortarDurationMultiplier).get();
        int processing = (int) Math.round(base * multiplier);
        return Math.max(1, Math.min(processing, MAX_PROCESSING_TICKS));
    }

    /** 取这次使用的<b>加工时长</b>（tick，不含起手）。 */
    public static int processingTicks(MortarContents contents, boolean crusherMortar) {
        int stored = contents.durationTicks();
        if (stored > 0) {
            return Math.min(stored, MAX_PROCESSING_TICKS);
        }
        return resolveDuration(null, crusherMortar);
    }

    /** 捶捣曲线里「砸到底」所在的周期相位（0~1）。 */
    public static final float SLAM_PHASE = 0.60f;

    /** 一个捶捣周期里「砸到底」发生在第几个 tick。 */
    public static int slamTick(int cycleTicks) {
        return Math.max(0, Math.min(cycleTicks - 1, Math.round(SLAM_PHASE * cycleTicks)));
    }

    public MortarItem(Properties properties) {
        super(properties);
    }

    // ================= 右键：副手取物，开始研磨 =================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        MortarContents loaded = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (loaded != null) {
            StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                    windupTicks(false), processingTicks(loaded, false));
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack input = player.getItemInHand(otherHand);
        if (input.isEmpty()) {
            return InteractionResult.FAIL;
        }

        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        RecipeHolder<?> recipe = findMillingRecipe(level, recipeInput);
        if (recipe == null) {
            return InteractionResult.FAIL;
        }

        int duration = resolveDuration(recipe, false);

        ItemStack toGrind = input.copyWithCount(1);
        if (!level.isClientSide()) {
            input.shrink(1);
            player.setItemInHand(otherHand, input);
        }
        stack.set(ModDataComponents.MORTAR_CONTENTS, new MortarContents(toGrind, duration));

        StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                windupTicks(false), duration);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // ================= 使用时长 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null || contents.stack().isEmpty()) {
            return 0;
        }
        int processing = processingTicks(contents, false);
        int firstUse = windupTicks(false) + processing;
        if (entity instanceof Player player) {
            return StickyUseSession.currentUseDuration(player, stack, firstUse);
        }
        return firstUse;
    }

    // ================= 完成：产出研磨结果 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return stack;
        }

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null || contents.stack().isEmpty()) {
            return stack;
        }

        if (level.isClientSide()) {
            return stack;
        }

        int windup = windupTicks(false);
        int cycle = poundCycleTicks(false);
        int phase = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        int duration = getUseDuration(stack, entity);

        SingleRecipeInput recipeInput = new SingleRecipeInput(contents.stack());
        RecipeHolder<?> recipe = findMillingRecipe(level, recipeInput);
        if (recipe != null && recipe.value() instanceof CreateSingleStackRollableRecipe rollable) {
            List<ItemStack> results = rollable.assemble(recipeInput, level.getRandom());
            for (ItemStack result : results) {
                if (!result.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(result);
                }
            }
        }

        stack.remove(ModDataComponents.MORTAR_CONTENTS);
        stack.hurtAndBreak(1, entity, entity.getUsedItemHand());

        boolean cycleSoundPlayed = phase >= 0 && CycleSoundWindow.hasCycleTick(
                phase - (duration - 1), phase - 1, cycle, slamTick(cycle));
        if (!cycleSoundPlayed) {
            level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 1f, 2.0f + (level.getRandom().nextFloat() - 0.5f) * 0.2f);
        }
        return stack;
    }

    // ================= 中途松手：退还物品 =================

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        if (level.isClientSide()) {
            return false;
        }

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null) {
            return false;
        }

        ItemStack input = contents.stack();
        if (!input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(input);
        }
        stack.remove(ModDataComponents.MORTAR_CONTENTS);
        return true;
    }

    // ================= 动画 / 音效屏蔽 =================

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null || contents.stack().isEmpty()) {
            return;
        }
        ItemStack input = contents.stack();
        if (!(entity instanceof Player player)) {
            return;
        }

        int windup = windupTicks(false);
        int cycle = poundCycleTicks(false);

        int poundTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        if (poundTicks < 0) {
            return;
        }
        if (poundTicks % cycle != slamTick(cycle)) {
            return;
        }

        if (!level.isClientSide()) {
            if (remainingTicks > 1) {
                level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                        SoundSource.PLAYERS, 1f, 2.0f + (level.getRandom().nextFloat() - 0.5f) * 0.2f);
            }

            if (level instanceof ServerLevel serverLevel) {
                Vec3 eye = entity.getEyePosition(1f);
                Vec3 look = entity.getLookAngle();
                Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2;
                    double horizSpeed = 0.05 + level.getRandom().nextDouble() * 0.1;
                    double vx = Math.cos(angle) * horizSpeed;
                    double vz = Math.sin(angle) * horizSpeed;
                    double vy = 0.15 + level.getRandom().nextDouble() * 0.15;

                    serverLevel.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(input)),
                            spawnPos.x, spawnPos.y, spawnPos.z,
                            1, vx, vy, vz, 0.0
                    );
                }
            }
        }
    }

    // ================= 配方查找：走统一配方池 =================

    @Nullable
    static RecipeHolder<?> findMillingRecipe(Level level, SingleRecipeInput recipeInput) {
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.MORTAR, level)) {
            if (holder.value() instanceof HandMadeCrushingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.MORTAR) {
                    continue;
                }
                if (!exclusive.matches(recipeInput, level)) {
                    continue;
                }
                return holder;
            }
            if (!(holder.value() instanceof MillingRecipe millingRecipe)) {
                continue;
            }
            if (!millingRecipe.matches(recipeInput, level)) {
                continue;
            }
            return holder;
        }
        return null;
    }
}

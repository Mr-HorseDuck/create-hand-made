package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.zurrtum.create.content.kinetics.crusher.CrushingRecipe;
import com.zurrtum.create.content.kinetics.millstone.MillingRecipe;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CrusherMortarItem extends Item {

    public CrusherMortarItem(Properties properties) {
        super(properties);
    }

    // ================= 右键：副手取物，开始加工 =================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        MortarContents loaded = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (loaded != null) {
            // 必须在 startUsingItem 之前定下“本次使用的总时长”（见 MortarItem 同处注释）
            StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                    MortarItem.windupTicks(true), MortarItem.processingTicks(loaded, true));
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack input = player.getItemInHand(otherHand);
        if (input.isEmpty()) {
            return InteractionResult.FAIL;
        }

        // 先粉碎、后研磨
        RecipeHolder<?> recipe = findRecipe(level, input);
        if (recipe == null) {
            return InteractionResult.FAIL;
        }

        // 加工时长在这里定死（不含起手）：getUseDuration 每帧都会被调用，不能在那里查配方。
        int duration = MortarItem.resolveDuration(recipe, true);

        ItemStack toGrind = input.copyWithCount(1);
        if (!level.isClientSide()) {
            input.shrink(1);
            player.setItemInHand(otherHand, input);
        }
        stack.set(ModDataComponents.MORTAR_CONTENTS, new MortarContents(toGrind, duration));

        StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                MortarItem.windupTicks(true), duration);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // ================= 使用时长：起手 + 加工时长 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null || contents.stack().isEmpty()) {
            return 0;
        }
        int processing = MortarItem.processingTicks(contents, true);
        int firstUse = MortarItem.windupTicks(true) + processing;
        if (entity instanceof Player player) {
            return StickyUseSession.currentUseDuration(player, stack, firstUse);
        }
        return firstUse;
    }

    // ================= 完成：产出结果 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return stack;
        }

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents == null || contents.stack().isEmpty()) {
            return stack;
        }

        // 客户端提前返回：产出、数据组件清除、耐久消耗全部交给服务端权威执行
        if (level.isClientSide()) {
            return stack;
        }

        // 完成音“保底”要用的两个量必须在这里先取（getUseDuration 依赖数据组件，下面会清掉它）
        int windup = MortarItem.windupTicks(true);
        int cycle = MortarItem.poundCycleTicks(true);
        int phase = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        int duration = getUseDuration(stack, entity);

        SingleRecipeInput recipeInput = new SingleRecipeInput(contents.stack());
        RecipeHolder<?> recipe = findRecipe(level, contents.stack());
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

        // 本件一次捶捣音都没真的响过才补（音效/音量/音调与周期音完全一致）
        boolean cycleSoundPlayed = phase >= 0 && CycleSoundWindow.hasCycleTick(
                phase - (duration - 1), phase - 1, cycle, MortarItem.slamTick(cycle));
        if (!cycleSoundPlayed) {
            level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
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

        // 客户端提前返回：退还、组件清除交给服务端权威执行
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

    /**
     * 与 {@code MortarItem} 一致：使用姿势交给 {@code NONE}，只保留手臂的基础持握姿态。
     */
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

        int windup = MortarItem.windupTicks(true);
        int cycle = MortarItem.poundCycleTicks(true);

        int poundTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        if (poundTicks < 0) {
            return;   // 还在起手期内（或不在会话里）
        }
        if (poundTicks % cycle != MortarItem.slamTick(cycle)) {
            return;
        }

        // 服务端：音效 + 粒子广播给附近玩家
        if (!level.isClientSide()) {
            // 本次使用的最后一个 tick（remainingTicks == 1）就是产出 tick，周期音让位
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

    /**
     * 复刻 CrushingWheelControllerBlockEntity.findRecipe()：先粉碎、后研磨。
     *
     * <p>这个优先级现在由配方池保证 —— {@link HandMadeRecipePool#getBaseRecipes} 的
     * {@link HandMadeTool#CRUSHER_MORTAR} 列表已经把 CRUSHING 全部排在前面、MILLING 全部排在后面。</p>
     */
    @Nullable
    private static RecipeHolder<?> findRecipe(Level level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.CRUSHER_MORTAR, level)) {
            Recipe<?> recipe = holder.value();

            // L3 独占配方（碾磨家族）：碾钵接受自己写下的，也接受研钵写下的
            if (recipe instanceof HandMadeCrushingRecipe exclusive) {
                HandMadeTool owner = exclusive.getTool();
                if (owner != HandMadeTool.CRUSHER_MORTAR && owner != HandMadeTool.MORTAR) {
                    continue;
                }
                if (!exclusive.matches(recipeInput, level)) {
                    continue;
                }
                return holder;
            }

            boolean matched = (recipe instanceof CrushingRecipe crushing && crushing.matches(recipeInput, level))
                    || (recipe instanceof MillingRecipe milling && milling.matches(recipeInput, level));
            if (matched) {
                return holder;
            }
        }
        return null;
    }
}

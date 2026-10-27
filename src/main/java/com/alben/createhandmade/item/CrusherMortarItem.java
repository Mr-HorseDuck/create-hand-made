package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.foundation.item.CustomUseEffectsItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class CrusherMortarItem extends Item implements CustomUseEffectsItem {

    public CrusherMortarItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 右键：副手取物，开始加工 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        MortarContents loaded = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (loaded != null) {
            // ★ b2.6：必须在 startUsingItem 之前定下"本次使用的总时长"（见 MortarItem 同处注释）
            StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                    MortarItem.windupTicks(true), MortarItem.processingTicks(loaded, true));
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack input = player.getItemInHand(otherHand);
        if (input.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        // 先粉碎、后研磨
        RecipeHolder<?> recipe = findRecipe(level, input);
        if (recipe == null) {
            return InteractionResultHolder.fail(stack);
        }

        // 加工时长在这里定死（不含起手）：getUseDuration 每帧都会被调用，不能在那里查配方。
        // 用碾钵那一套配置键（系数 / 默认时长 / 上限逻辑与研钵共用实现）。
        int duration = MortarItem.resolveDuration(recipe, true);

        ItemStack toGrind = input.copyWithCount(1);
        if (!level.isClientSide) {
            input.shrink(1);
            player.setItemInHand(otherHand, input);
        }
        stack.set(ModDataComponents.MORTAR_CONTENTS.get(), new MortarContents(toGrind, duration));

        // ★ b2.6：装料这一次也算"会话首次使用"（紧接上一次松手 5t 内的换料视为续磨）
        StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                MortarItem.windupTicks(true), duration);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长：起手 + 加工时长 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return 0;
        // 与 MortarItem 同一套规则，只是读碾钵那一套配置键。
        // processingTicks 里含"旧数据（没有 duration_ticks）回退到默认时长"的兜底。
        int processing = MortarItem.processingTicks(contents, true);
        int firstUse = MortarItem.windupTicks(true) + processing;
        // ★ b2.6：总时长由 use() 里的 beginUse 定下、这里读回：会话首次 = 起手 + 加工，
        //   同一次长按的续用 = 只有加工。本方法每帧都会被调用，一次使用内必须恒定。
        if (entity instanceof Player player) {
            return StickyUseSession.currentUseDuration(player, stack, firstUse);
        }
        return firstUse;
    }

    // ================= 完成：产出结果 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return stack;

        // ★ 客户端提前返回：产出、数据组件清除、耐久消耗全部交给服务端权威执行
        if (level.isClientSide) return stack;

        // ★ b2.7c：完成音"保底"要用的两个量必须在这里先取（getUseDuration 依赖数据组件，
        //   而下面会清掉它）。phase = 本件最后一个 tick 的会话加工相位；duration = 本次使用总时长。
        int windup = MortarItem.windupTicks(true);
        int cycle = MortarItem.poundCycleTicks(true);
        int phase = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        int duration = getUseDuration(stack, entity);

        RecipeHolder<?> recipe = findRecipe(level, contents.stack());
        if (recipe != null && recipe.value() instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> pr) {
            List<ItemStack> results = pr.rollResults(level.random);
            for (ItemStack result : results) {
                if (!result.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(result);
                }
            }
        }

        stack.remove(ModDataComponents.MORTAR_CONTENTS.get());
        stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));

        // ★ b2.7c 完成音"保底"（取代 b2.7 的"无条件补"）：本件窗口 = [phase-(duration-1), phase]，
        //   最后一个 tick 已被 onUseTick 让位给这一声 ⇒ 只统计到 phase - 1。
        //   本件一次捶捣音都没真的响过才补（音效/音量/音调与周期音完全一致）。
        boolean cycleSoundPlayed = phase >= 0 && CycleSoundWindow.hasCycleTick(
                phase - (duration - 1), phase - 1, cycle, MortarItem.slamTick(cycle));
        if (!cycleSoundPlayed) {
            level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
        }
        return stack;
    }

    // ================= 中途松手：退还物品 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        // ★ 客户端提前返回：退还、组件清除交给服务端权威执行
        if (level.isClientSide) return;

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null) return;

        ItemStack input = contents.stack();
        if (!input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(input);
        }
        stack.remove(ModDataComponents.MORTAR_CONTENTS.get());
    }

    // ================= 动画 / 音效屏蔽 =================

    /**
     * 与 {@code MortarItem} 一致：使用姿势交给 {@link UseAnim#NONE}，只保留手臂的基础持握姿态，
     * 不再叠加 EAT/DRINK 那套「往嘴边抬」的位移与旋转 —— 否则手臂会同时做出啃食动作、
     * 与渲染器的钵/杵动画打架。
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEatingSound() {
        return SoundEvents.EMPTY;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return;
        ItemStack input = contents.stack();
        if (!(entity instanceof Player player)) return;

        // 与 MortarItem 完全一致的节奏（区别只是读碾钵那一套配置键）：
        // 1) 起手门槛 = 配置值（两端同值 ⇒ "服务端开始出声"与客户端起手同步）；
        // 2) 音效/粒子每「一个捶捣周期」一次，并对齐「砸到底」那一 tick
        //    （相位常量由 MortarItem.SLAM_PHASE 统一提供）。
        // ★ b2.6：相位改用「会话加工 tick」（见 MortarItem 同处注释）——
        //   首次使用与旧行为等价，续用不再每件静止一个起手时长。
        int windup = MortarItem.windupTicks(true);
        int cycle = MortarItem.poundCycleTicks(true);

        int poundTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        if (poundTicks < 0) return;   // 还在起手期内（或不在会话里）
        if (poundTicks % cycle != MortarItem.slamTick(cycle)) return;

        // ★ 服务端：音效 + 粒子广播给附近玩家
        if (!level.isClientSide) {
            // ★ b2.7c 让位：本次使用的最后一个 tick（remainingTicks == 1）就是产出 tick ——
            //   finishUsingItem 会在同一 tick 播"完成音"，所以那一 tick 的周期音让位（粒子照旧）。
            if (remainingTicks > 1) {
                level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                        SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
            }

            if (level instanceof ServerLevel serverLevel) {
                Vec3 eye = entity.getEyePosition(1f);
                Vec3 look = entity.getLookAngle();
                Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2;
                    double horizSpeed = 0.05 + level.random.nextDouble() * 0.1;
                    double vx = Math.cos(angle) * horizSpeed;
                    double vz = Math.sin(angle) * horizSpeed;
                    double vy = 0.15 + level.random.nextDouble() * 0.15;

                    serverLevel.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, input),
                            spawnPos.x, spawnPos.y, spawnPos.z,
                            1, vx, vy, vz, 0.0
                    );
                }
            }
        }
    }

    // ================= 渲染器 =================

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new MortarItemRenderer()));
    }

    // ================= 屏蔽原版进食效果 =================

    @Override
    public TriState shouldTriggerUseEffects(ItemStack stack, LivingEntity entity) {
        return TriState.TRUE;
    }

    @Override
    public boolean triggerUseEffects(ItemStack stack, LivingEntity entity, int count, RandomSource random) {
        return true;
    }

    // ================= 配方查找：走统一配方池 =================

    /**
     * 复刻 CrushingWheelControllerBlockEntity.findRecipe()：先粉碎、后研磨。
     *
     * <p>「先粉碎、后研磨」这个优先级现在由配方池保证 ——
     * {@link HandMadeRecipePool#getBaseRecipes} 的
     * {@link HandMadeTool#CRUSHER_MORTAR} 列表已经把 CRUSHING 全部排在前面、
     * MILLING 全部排在后面。所以这里<b>不再自己分两段查询</b>，只按顺序遍历、
     * 取第一个匹配即可，优先级与改造前完全一致。</p>
     *
     * <p>改造前的两段查询是 {@code AllRecipeTypes.CRUSHING.find(...)} 与
     * {@code AllRecipeTypes.MILLING.find(...)}，它们的实现都是
     * {@code level.getRecipeManager().getRecipeFor(...)}，等价于
     * 「按配方管理器顺序找第一条 {@code matches}」，与这里的顺序遍历 + matches 一致。</p>
     */
    @Nullable
    public static RecipeHolder<?> findRecipe(Level level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        // 用 instanceof 模式匹配取出确切类型再调 matches：
        // holder.value() 的静态类型是 Recipe<?>，其 matches 的参数是通配符捕获，
        // 无法直接接受 SingleRecipeInput。这样既不需要 unchecked 强转，
        // 也不会在将来类型变化时静默出错。
        // CRUSHING 与 MILLING 这两段各自的顺序由配方池保证。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.CRUSHER_MORTAR, level)) {
            Recipe<?> recipe = holder.value();

            // L3 独占配方（碾磨家族）：碾钵接受自己写下的，也接受研钵写下的
            // （L1 语义就是"先粉碎、后研磨"，它连 Create 的 MILLING 都能读）
            if (recipe instanceof HandMadeCrushingRecipe exclusive) {
                HandMadeTool owner = exclusive.getTool();
                if (owner != HandMadeTool.CRUSHER_MORTAR && owner != HandMadeTool.MORTAR) continue;
                if (!exclusive.matches(recipeInput, level)) continue;
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
package com.alben.createhandmade.item;

import com.alben.createhandmade.enchantment.BatchCraftingHelper;
import com.alben.createhandmade.item.contents.MortarContents;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.Config;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MortarItem extends Item implements CustomUseEffectsItem {

    int windupTicks = Config.INSTANCE.mortarWindupTicks.get();   // 碾钵用 crusherMortarWindupTicks
    int processingTicks = Config.INSTANCE.mortarProcessingTicks.get();   // 碾钵用 crusherMortarProcessingTicks

    /** ★ 保底音标记：本次加工是否已经播过常规研磨音 */
    private static final String NBT_SOUND_PLAYED = "HandMadeGrindSoundPlayed";

    public MortarItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 右键：副手取物，开始研磨 =================

    /**
     * ★ 批量加工：读附魔等级，从副手一次性取 N 份塞进 MortarContents。
     *   实际取数受副手数量限制。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (MortarContents.fromStack(stack) != null) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack input = player.getItemInHand(otherHand);
        if (input.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        if (findRecipe(level, input) == null) {
            return InteractionResultHolder.fail(stack);
        }

        // ★ 批量：读附魔等级，受副手数量限制
        int batchSize = BatchCraftingHelper.getBatchSize(stack);
        int take = BatchCraftingHelper.clampToAvailable(batchSize, input.getCount());
        if (take <= 0) take = 1;

        ItemStack toGrind = input.copyWithCount(take);
        if (!level.isClientSide) {
            input.shrink(take);
            player.setItemInHand(otherHand, input);
        }
        new MortarContents(toGrind).writeToStack(stack);

        // ★ 重置保底音标记：新一轮加工开始
        stack.getOrCreateTag().putBoolean(NBT_SOUND_PLAYED, false);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长：固定 100 =================

    @Override
    public int getUseDuration(ItemStack stack) {
    MortarContents contents = MortarContents.fromStack(stack);
    if (contents == null || contents.stack().isEmpty()) return 0;
    int windupTicks = Config.INSTANCE.mortarWindupTicks.get();
    int processingTicks = Config.INSTANCE.mortarProcessingTicks.get();
    return windupTicks + processingTicks;
    }

    // ================= 完成：产出研磨结果 =================

    /**
     * ★ 批量加工：MortarContents 里的数量即实际加工份数，产物 × count。
     * ★ 保底音：如果本次加工 onUseTick 一次都没播过研磨音（加工时间极短时可能发生），
     *   在这里补一声。
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        MortarContents contents = MortarContents.fromStack(stack);
        if (contents == null || contents.stack().isEmpty()) return stack;

        if (level.isClientSide) return stack;

        // ★ 批量：以 MortarContents 里的数量为准（1 级=2, 2级=4, 3级=8，受副手限制）
        int count = contents.stack().getCount();
        if (count <= 0) count = 1;

        boolean produced = false;
        Recipe<?> recipe = findRecipe(level, contents.stack());
        if (recipe instanceof ProcessingRecipe<?> pr) {
            List<ItemStack> singleResults = pr.rollResults();
            // 产物 × count，按 maxStackSize 拆分发放
            for (ItemStack single : singleResults) {
                if (single.isEmpty()) continue;
                produced = true;
                int total = single.getCount() * count;
                int max = single.getMaxStackSize();
                while (total > 0) {
                    int give = Math.min(total, max);
                    ItemStack copy = single.copy();
                    copy.setCount(give);
                    player.getInventory().placeItemBackInInventory(copy);
                    total -= give;
                }
            }
        }

        // ★ 保底音：加工确实产出了东西，但常规音效一次都没播
        if (produced && !stack.getOrCreateTag().getBoolean(NBT_SOUND_PLAYED)) {
            level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 0.8f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
        }

        MortarContents.clearFromStack(stack);
        stack.removeTagKey(NBT_SOUND_PLAYED);

        EquipmentSlot slot = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(slot));
        return stack;
    }

    // ================= 中途松手：退还物品 =================

    /**
     * ★ 批量：contents.stack() 已经是 take 份的整栈，整个退回去即可。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        MortarContents contents = MortarContents.fromStack(stack);
        if (contents == null) return;

        ItemStack input = contents.stack();
        if (!input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(input);
        }
        MortarContents.clearFromStack(stack);
        stack.removeTagKey(NBT_SOUND_PLAYED);
    }

    // ================= 三层配方查询 =================

    @Nullable
    public static Recipe<?> findRecipe(Level level, ItemStack input) {
        if (input.isEmpty()) return null;

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        // ---- 段 1：L3 独立 type ----
        for (ProcessingRecipe<RecipeWrapper> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipeLike like)) continue;
            if (like.getTool() != ToolType.MORTAR) continue;
            if (!r.matches(wrapper, level)) continue;
            return r;
        }

        // ---- 段 2：L1 Create 的 MILLING ----
        Optional<MillingRecipe> recipeOpt = AllRecipeTypes.MILLING.find(wrapper, level);
        if (recipeOpt.isEmpty()) return null;

        // ---- L2 过滤 ----
        List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                ToolType.MORTAR, level, List.of(recipeOpt.get()));
        return filtered.isEmpty() ? null : filtered.get(0);
    }

    // ================= 女仆一次性研磨辅助 =================

    public static boolean tryGrindOnce(Level level, LivingEntity entity, ItemStack mortar,
                                        @Nullable IItemHandler inputInv,
                                        @Nullable IItemHandler outputInv,
                                        @Nullable FilterItemStack filter) {
        if (level.isClientSide) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        ItemStack material = ItemStack.EMPTY;
        boolean fromOffhand = false;
        int fromInputSlot = -1;

        ItemStack off = entity.getOffhandItem();
        if (!off.isEmpty() && findRecipe(level, off) != null) {
            material = off.copyWithCount(1);
            fromOffhand = true;
        } else if (inputInv != null) {
            for (int i = 0; i < inputInv.getSlots(); i++) {
                ItemStack slot = inputInv.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (findRecipe(level, slot) == null) continue;
                material = inputInv.extractItem(i, 1, false);
                if (!material.isEmpty()) {
                    fromInputSlot = i;
                    break;
                }
            }
        }

        if (material.isEmpty()) return false;

        Recipe<?> recipe = findRecipe(level, material);
        if (!(recipe instanceof ProcessingRecipe<?> pr)) {
            rollbackMaterial(entity, inputInv, material, fromOffhand, fromInputSlot);
            return false;
        }

        if (fromOffhand) {
            off.shrink(1);
        }

        List<ItemStack> results = pr.rollResults();
        List<ItemStack> accepted = new ArrayList<>();
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            if (filter != null && !filter.test(level, result)) continue;
            accepted.add(result);
        }

        if (accepted.isEmpty()) {
            rollbackMaterial(entity, inputInv, material, fromOffhand, fromInputSlot);
            return false;
        }

        for (ItemStack result : accepted) {
            ItemStack remaining = result.copy();

            if (outputInv != null) {
                for (int i = 0; i < outputInv.getSlots(); i++) {
                    remaining = outputInv.insertItem(i, remaining, false);
                    if (remaining.isEmpty()) break;
                }
            }

            if (!remaining.isEmpty()) {
                if (entity instanceof Player player) {
                    player.getInventory().placeItemBackInInventory(remaining);
                } else {
                    entity.spawnAtLocation(remaining);
                }
            }
        }

        level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);

        Vec3 eye = entity.getEyePosition(1f);
        Vec3 look = entity.getLookAngle();
        Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

        for (int i = 0; i < 8; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double horizSpeed = 0.05 + level.random.nextDouble() * 0.1;
            double vx = Math.cos(angle) * horizSpeed;
            double vz = Math.sin(angle) * horizSpeed;
            double vy = 0.15 + level.random.nextDouble() * 0.15;

            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, material),
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    1, vx, vy, vz, 0.0
            );
        }

        EquipmentSlot slot = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        mortar.hurtAndBreak(1, entity, e -> {
            if (e instanceof Player p) {
                p.broadcastBreakEvent(slot);
            }
        });

        return true;
    }

    private static void rollbackMaterial(LivingEntity entity, @Nullable IItemHandler inputInv,
                                          ItemStack material, boolean fromOffhand, int fromInputSlot) {
        if (material.isEmpty()) return;

        if (fromOffhand) {
            ItemStack off = entity.getOffhandItem();
            if (off.isEmpty()) {
                entity.setItemInHand(InteractionHand.OFF_HAND, material);
            } else if (ItemStack.isSameItemSameTags(off, material) && off.getCount() < off.getMaxStackSize()) {
                off.grow(1);
            } else {
                entity.spawnAtLocation(material);
            }
            return;
        }

        if (inputInv != null) {
            if (fromInputSlot >= 0 && fromInputSlot < inputInv.getSlots()) {
                ItemStack leftover = inputInv.insertItem(fromInputSlot, material, false);
                if (leftover.isEmpty()) return;
                material = leftover;
            }
            for (int i = 0; i < inputInv.getSlots(); i++) {
                material = inputInv.insertItem(i, material, false);
                if (material.isEmpty()) return;
            }
        }

        entity.spawnAtLocation(material);
    }

    // ================= 动画 / 音效屏蔽 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEatingSound() {
        return SoundEvents.EMPTY;
    }

    // ================= 每 tick：音效 + 粒子 =================

    /**
     * ★ 播了常规研磨音后，标记 NBT_SOUND_PLAYED = true，供 finishUsingItem 判断是否需要保底音。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        MortarContents contents = MortarContents.fromStack(stack);
        if (contents == null || contents.stack().isEmpty()) return;
        ItemStack input = contents.stack();

        int totalDuration = getUseDuration(stack);
        int usedTicks = totalDuration - remainingTicks;

        if (usedTicks < Config.INSTANCE.mortarWindupTicks.get() || usedTicks % 4 != 0) return;

        if (!level.isClientSide) {
            level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);

            // ★ 标记：本次加工已经播过常规音
            stack.getOrCreateTag().putBoolean(NBT_SOUND_PLAYED, true);

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
}
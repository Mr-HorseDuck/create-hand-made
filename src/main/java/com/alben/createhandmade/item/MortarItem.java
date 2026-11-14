package com.alben.createhandmade.item;

import com.alben.createhandmade.enchantment.BatchCrafting;
import com.alben.createhandmade.item.contents.MortarContents;
import com.alben.createhandmade.item.renderer.MortarItemRenderer;
import com.alben.createhandmade.api.HandMadeResult;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.config.SyncedServerConfig;
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

    // 加工时长每次从同步镜像读取（服务端权威 + 配置重载会重新下发），
    // 既支持热重载，也让客户端 getUseDuration / onUseTick 与服务端一致；不要改回构造期快照字段。

    public MortarItem(Properties properties) {
        super(properties);
    }

    /** 起手时长；研钵读自己的配置段。 */
    protected int getWindupTicks() {
        return SyncedServerConfig.mortarWindup();
    }

    /** 加工时长；研钵读自己的配置段。 */
    protected int getProcessingTicks() {
        return SyncedServerConfig.mortarProcessing();
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
        int batchSize = BatchCrafting.getBatchSize(stack);
        int take = BatchCrafting.clampToAvailable(batchSize, input.getCount());
        if (take <= 0) take = 1;

        ItemStack toGrind = input.copyWithCount(take);
        if (!level.isClientSide) {
            input.shrink(take);
            player.setItemInHand(otherHand, input);
        }
        new MortarContents(toGrind).writeToStack(stack);

        // ★ 重置保底音标记：新一轮加工开始
        stack.getOrCreateTag().putBoolean(MortarGrindEffects.NBT_SOUND_PLAYED, false);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长 =================

    @Override
    public int getUseDuration(ItemStack stack) {
        MortarContents contents = MortarContents.fromStack(stack);
        if (contents == null || contents.stack().isEmpty()) return 0;
        return getWindupTicks() + getProcessingTicks();
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
            // ★ 逐份摇：chance / 加权产物必须每份独立判定。之前是 rollResults() 摇一次
            //   再把数量乘 count，等于 N 份同生共死（要么全出、要么全不出）。
            //   摇出空的那一份按"白磨"处理 —— 不退还原料，和机械研磨一致。
            for (int i = 0; i < count; i++) {
                for (ItemStack rolled : pr.rollResults()) {
                    if (rolled.isEmpty()) continue;
                    produced = true;
                    player.getInventory().placeItemBackInInventory(rolled.copy());
                }
            }
        }

        // ★ 保底音：加工确实产出了东西，但常规音效一次都没播
        if (produced && !stack.getOrCreateTag().getBoolean(MortarGrindEffects.NBT_SOUND_PLAYED)) {
            level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 0.8f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
        }

        MortarContents.clearFromStack(stack);
        stack.removeTagKey(MortarGrindEffects.NBT_SOUND_PLAYED);

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
        stack.removeTagKey(MortarGrindEffects.NBT_SOUND_PLAYED);
    }

    // ================= 三层配方查询 =================

    /** 单格输入的配方 wrapper；L3 查询按需创建用（绝大多数整合包没有 L3 配方，先建就是白分配）。 */
    private static RecipeWrapper wrapSingle(ItemStack input) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        return new RecipeWrapper(handler);
    }

    @Nullable
    public static Recipe<?> findRecipe(Level level, ItemStack input) {
        if (input.isEmpty()) return null;

        // ★ 懒创建：没有 L3 配方时不必提前分配（女仆研钵行为每个冷却窗口会走到这里）
        RecipeWrapper wrapper = null;

        // ---- 段 1：L3 独立 type ----
        for (ProcessingRecipe<RecipeWrapper> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipeLike like)) continue;
            if (like.getTool() != ToolType.MORTAR) continue;
            if (!HandMadeRecipePool.isAvailable(ToolType.MORTAR, level, r)) continue;
            if (wrapper == null) wrapper = wrapSingle(input);
            if (!r.matches(wrapper, level)) continue;
            return r;
        }

        // ---- 段 2：L1 Create 的 MILLING ----
        // 段 1 没建过 wrapper（没有 L3 配方）时在这里补上
        if (wrapper == null) wrapper = wrapSingle(input);
        Optional<MillingRecipe> recipeOpt = AllRecipeTypes.MILLING.find(wrapper, level);
        if (recipeOpt.isEmpty()) return null;

        // ---- L2 过滤 ----
        if (!HandMadeRecipePool.isAvailable(ToolType.MORTAR, level, recipeOpt.get())) return null;
        return recipeOpt.get();
    }

    // ================= 女仆一次性研磨辅助 =================

    /** 旧签名（boolean）。内部调用方继续用它。 */
    public static boolean tryGrindOnce(Level level, LivingEntity entity, ItemStack mortar,
                                        @Nullable IItemHandler inputInv,
                                        @Nullable IItemHandler outputInv,
                                        @Nullable FilterItemStack filter) {
        return tryGrindOnce(level, entity, mortar, inputInv, outputInv, filter, false).isSuccess();
    }

    /**
     * 一次性研磨（真正实现）。
     *
     * <p>★ 判定与执行分离：先找「候选材料 + 配方 + 过滤器放行」，全通过才真的从副手 / 输入容器
     * 取料。{@code simulate} 为 true 时就在判定通过后返回，不取料、不摇结果、不扣耐久、不播音效。</p>
     *
     * <p>旧实现是「先 extractItem 再判定」，失败再 rollback——那样根本没法做预演，
     * 而且 rollback 自身的分支（副手已空 / 容器已满）容易出漏洞。改成先判定后取料后，
     * rollbackMaterial 不再需要，已连带删除。</p>
     */
    public static HandMadeResult tryGrindOnce(Level level, LivingEntity entity, ItemStack mortar,
                                              @Nullable IItemHandler inputInv,
                                              @Nullable IItemHandler outputInv,
                                              @Nullable FilterItemStack filter, boolean simulate) {
        if (level.isClientSide) return HandMadeResult.CLIENT_SIDE;
        if (!(level instanceof ServerLevel serverLevel)) return HandMadeResult.INVALID_TARGET;

        // ---- 1. 找候选材料（此时不取料）----
        ItemStack candidate = ItemStack.EMPTY;
        boolean fromOffhand = false;
        int fromInputSlot = -1;

        ItemStack off = entity.getOffhandItem();
        if (!off.isEmpty() && findRecipe(level, off) != null) {
            candidate = off.copyWithCount(1);
            fromOffhand = true;
        } else if (inputInv != null) {
            for (int i = 0; i < inputInv.getSlots(); i++) {
                ItemStack slot = inputInv.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (findRecipe(level, slot) == null) continue;
                candidate = slot.copyWithCount(1);
                fromInputSlot = i;
                break;
            }
        }

        if (candidate.isEmpty()) return HandMadeResult.NO_INPUT;

        // ---- 2. 配方 ----
        Recipe<?> recipe = findRecipe(level, candidate);
        if (!(recipe instanceof ProcessingRecipe<?> pr)) return HandMadeResult.NO_RECIPE;

        // ---- 3. 过滤器：摇结果只为看有没有放行的；rollResults 是纯计算，无副作用 ----
        List<ItemStack> accepted = new ArrayList<>();
        for (ItemStack result : pr.rollResults()) {
            if (result.isEmpty()) continue;
            if (filter != null && !filter.test(level, result)) continue;
            accepted.add(result);
        }
        if (accepted.isEmpty()) return HandMadeResult.FILTERED_OUT;

        if (simulate) return HandMadeResult.SUCCESS;

        // ---- 4. 真正取料 ----
        ItemStack material;
        if (fromOffhand) {
            off.shrink(1);
            material = candidate;
        } else {
            material = inputInv.extractItem(fromInputSlot, 1, false);
            if (material.isEmpty()) return HandMadeResult.NO_INPUT;
        }

        // ---- 5. 产出 ----
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

        return HandMadeResult.SUCCESS;
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

    /** 公共研磨音/粒子逻辑在 MortarGrindEffects，这里只提供研钵自己的 windupTicks。 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        MortarContents contents = MortarContents.fromStack(stack);
        if (contents == null || contents.stack().isEmpty()) return;
        MortarGrindEffects.tick(level, entity, stack, contents.stack(),
                getUseDuration(stack) - remainingTicks, getWindupTicks());
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
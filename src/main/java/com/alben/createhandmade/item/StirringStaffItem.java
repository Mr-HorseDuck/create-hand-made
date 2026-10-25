package com.alben.createhandmade.item;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.network.StirringStatePacket;
import com.alben.createhandmade.recipe.BasinRecipeCompat;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinInput;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StirringStaffItem extends Item {

    // 起手 / 加工时长已移到 ServerConfig（tools.stirring_staff.*），运行时读 —— 见 getUseDuration() 与 onUseTick()。

    public StirringStaffItem(Properties properties) {
        super(properties);
    }

    /**
     * 配方诊断日志开关：{@code -Dcreate_hand_made.debugRecipes=true}。
     * <p>打印工作盆坐标、盆内物品与三类候选的数量，用来区分「没进候选集」和「进了但没匹配上」。</p>
     */
    private static final boolean DEBUG_RECIPES = Boolean.getBoolean("create_hand_made.debugRecipes");

    // ================= 右键拦截：把“工作盆的空手交互”让给搅拌杖 =================
    //
    // 原版 Minecraft#startUseItem 在准星命中方块时，只要当前手的 useItemOn 返回
    // SUCCESS/CONSUME 就直接 return，副手根本不会被尝试。而 Create 的工作盆在空手时
    // useItemOn 无条件返回 SUCCESS（把盆里的东西掏回背包），于是“主手空着 + 搅拌杖在副手”
    // 永远走不到搅拌杖的 use()，只有潜行（跳过方块交互）才用得出。
    //
    // Fabric 用 UseBlockCallback 取代 PlayerInteractEvent.RightClickBlock：命中时直接调用
    // 搅拌杖的 use()，再返回 SUCCESS 抑制工作盆自身的交互。

    public static InteractionResult interceptRightClick(Player player, Level level, InteractionHand hand,
                                                        BlockHitResult hit) {
        // ★ 主手空着、搅拌杖在副手时，原版在「主手 useItemOn 返回 SUCCESS」处就 return 了，
        //   副手根本轮不到。这里显式回退到另一只手，保证副手搅拌杖也能用。
        InteractionHand useHand = hand;
        ItemStack stack = player.getItemInHand(useHand);
        if (!(stack.getItem() instanceof StirringStaffItem)) {
            // 只在主手为空时才回退，避免抢走"主手拿物品往盆里放"的正常交互
            if (!stack.isEmpty()) {
                return InteractionResult.PASS;
            }
            useHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            stack = player.getItemInHand(useHand);
            if (!(stack.getItem() instanceof StirringStaffItem)) {
                return InteractionResult.PASS;
            }
        }
        StirringStaffItem staff = (StirringStaffItem) stack.getItem();

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return InteractionResult.PASS;
        }
        if (basin.isEmpty()) {
            return InteractionResult.PASS;
        }

        staff.use(level, player, useHand);
        // 无论 use() 是否真的开始（配方不匹配），都要抑制工作盆的“掏东西”交互
        return InteractionResult.CONSUME;
    }

    // ================= 右键 =================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return InteractionResult.FAIL;
        }
        if (basin.isEmpty()) {
            return InteractionResult.FAIL;
        }

        // 26.1 客户端拿不到配方表：客户端只做结构判定，配方检测留给服务端。
        if (!level.isClientSide() && findMatchingRecipe(level, basin) == null) {
            return InteractionResult.FAIL;
        }

        StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                ServerConfig.INSTANCE.stirringStaffWindupTicks.get(),
                Math.max(1, ServerConfig.INSTANCE.stirringStaffProcessingTicks.get()));
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // ================= 使用时长 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int windupTicks = ServerConfig.INSTANCE.stirringStaffWindupTicks.get();
        int processingTicks = Math.max(1, ServerConfig.INSTANCE.stirringStaffProcessingTicks.get());
        int firstUse = windupTicks + processingTicks;
        if (entity instanceof Player player) {
            return StickyUseSession.currentUseDuration(player, stack, firstUse);
        }
        return firstUse;
    }

    // ================= 完成 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return stack;
        }
        if (level.isClientSide()) {
            return stack;
        }

        int windupTicks = ServerConfig.INSTANCE.stirringStaffWindupTicks.get();
        int phase = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windupTicks);
        int duration = getUseDuration(stack, entity);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return stack;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return stack;
        }

        // 再检测一次（防中途玩家挪动盆内容）
        RecipeHolder<?> recipe = findMatchingRecipe(level, basin);
        if (recipe != null && BasinRecipeCompat.apply(new BasinInput(basin), recipe.value(), level)) {
            basin.notifyChangeOfContents();
            stack.hurtAndBreak(1, entity, entity.getUsedItemHand());

            // 完成音“保底”：本件窗口 = [phase - (duration - 1), phase]，
            // 最后一个 tick 已被 onUseTick 让位给这一声 ⇒ 只统计到 phase - 1。
            boolean cycleSoundPlayed = phase >= 0
                    && CycleSoundWindow.hasCycleTick(phase - (duration - 1), phase - 1, 4, 0);
            if (!cycleSoundPlayed) {
                AllSoundEvents.MIXING.playOnServer(level, pos, 3.0f, 2f);
            }
        }
        return stack;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }

        int windupTicks = ServerConfig.INSTANCE.stirringStaffWindupTicks.get();
        int stirTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windupTicks);
        if (stirTicks < 0) {
            return;   // 还在起手期内（或不在会话里）
        }

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return;
        }
        if (basin.isEmpty()) {
            return;
        }

        // 服务端广播：音效 + 搅拌状态 + 粒子，全部同步给附近玩家
        if (!level.isClientSide() && stirTicks % 4 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                StirringStatePacket packet = new StirringStatePacket(pos);
                double rangeSq = 64.0 * 64.0;
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                        ServerPlayNetworking.send(p, packet);
                    }
                }

                // 本次使用的最后一个 tick（remainingTicks == 1）就是产出 tick，搅拌音让位
                if (remainingTicks > 1) {
                    AllSoundEvents.MIXING.playOnServer(level, pos, 3.0f, 2f);
                }
                broadcastStirParticles(level, pos, basin);
            }
        }
    }

    private static void broadcastStirParticles(Level level, BlockPos pos, BasinBlockEntity basin) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < basin.itemCapability.getContainerSize(); i++) {
            ItemStack s = basin.itemCapability.getItem(i);
            if (!s.isEmpty()) {
                stacks.add(s.copy());
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PressParticlesPacket packet = new PressParticlesPacket(
                pos, stacks, ParticleStyle.STIR);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ServerPlayNetworking.send(p, packet);
            }
        }
    }

    // ================= 配方查找：MIXING + 无序合成 + 自动酿造 =================

    @Nullable
    public static RecipeHolder<?> findMatchingRecipe(Level level, BasinBlockEntity basin) {
        if (basin.isEmpty()) {
            return null;
        }

        // 三类候选都来自统一配方池，并按配方管理器的全局顺序合并 ——
        // 与改造前 RecipeFinder.get(level, predicate) 的遍历顺序一致。
        //
        // 注意：Create Fly 把“自动酿造”改成了真正的 POTION 配方类型（PotionRecipe），
        // 因此不再需要原来 PotionMixingRecipes.sortRecipesByItem 的特例路径。
        //
        // try/catch 刻意保留：任何一条畸形配方抛异常都不应该让整个搅拌流程中断。
        try {
            List<RecipeHolder<?>> mixing =
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF, level);
            List<RecipeHolder<?>> shapeless =
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF_AUTO_SHAPELESS, level);
            List<RecipeHolder<?>> brewing =
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF_AUTO_BREWING, level);
            List<RecipeHolder<?>> merged =
                    HandMadeRecipePool.mergeInGlobalOrder(level, mixing, shapeless, brewing);
            BasinInput input = new BasinInput(basin);
            for (RecipeHolder<?> holder : merged) {
                if (BasinRecipeCompat.matches(input, holder.value(), level)) {
                    if (DEBUG_RECIPES) {
                        debugRecipes(basin, "MATCH " + holder.id().identifier()
                                + " (mixing=" + mixing.size() + ", shapeless=" + shapeless.size()
                                + ", brewing=" + brewing.size() + ", merged=" + merged.size() + ")");
                    }
                    return holder;
                }
            }
            if (DEBUG_RECIPES) {
                debugRecipes(basin, "no match (mixing=" + mixing.size() + ", shapeless=" + shapeless.size()
                        + ", brewing=" + brewing.size() + ", merged=" + merged.size() + ")");
            }
        } catch (Exception e) {
            // 防御畸形配方导致整个搅拌中断
            if (DEBUG_RECIPES) {
                debugRecipes(basin, "exception: " + e);
            }
        }

        return null;
    }

    private static void debugRecipes(BasinBlockEntity basin, String message) {
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < basin.itemCapability.getContainerSize(); i++) {
            ItemStack s = basin.itemCapability.getItem(i);
            if (!s.isEmpty()) {
                contents.add(s.getCount() + "x" + s.getItem());
            }
        }
        System.out.println("[CHM-RECIPE] " + basin.getBlockPos() + " " + message + " items=" + contents);
    }

    // ================= 动画 =================

    /**
     * 使用姿势交给 {@code NONE}，只保留手臂的基础持握姿态。
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }
}

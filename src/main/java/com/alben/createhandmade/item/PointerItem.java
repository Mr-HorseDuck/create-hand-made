package com.alben.createhandmade.item;

import com.alben.createhandmade.network.HighlightBlockPacket;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.HandMadeApplicationRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationInput;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import com.zurrtum.create.foundation.recipe.CreateRollableRecipe;
import com.zurrtum.create.foundation.recipe.RecipeApplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PointerItem extends Item {

    public PointerItem(Properties properties) {
        super(properties);
    }

    // ================= 置物台拦截 =================
    //
    // 原 NeoForge 版在 PlayerInteractEvent.RightClickBlock(HIGHEST) 里取消事件。
    // Fabric 用 UseBlockCallback：命中时返回 SUCCESS 抑制置物台自身的 use，
    // 服务端在回调内直接执行加工。

    public static InteractionResult interceptRightClick(Player player, Level level, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        ItemStack tool = player.getOffhandItem();
        if (tool.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot)) {
            return InteractionResult.PASS;
        }
        TransportedItemStack held = depot.getHeldItem();
        if (held == null || held.stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            // 26.1 客户端拿不到配方表，这里只负责抑制方块交互；真正的判定在服务端。
            return InteractionResult.SUCCESS;
        }

        RecipeHolder<?> recipe = findRecipe(level, held.stack, tool);
        if (recipe == null) {
            return InteractionResult.PASS;
        }

        executeDepotProcess(player, level, pos, tool, held.stack, recipe, depot);
        return InteractionResult.SUCCESS_SERVER;
    }

    // ================= 置物台加工 =================

    private static void executeDepotProcess(Player player, Level level, BlockPos pos,
                                            ItemStack tool, ItemStack held, RecipeHolder<?> recipe,
                                            DepotBlockEntity depot) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return;
        }

        ItemStack particleItem = held.copy();
        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) {
                return TransportedResult.doNothing();
            }
            ItemStack stack = item.stack;
            if (stack.isEmpty()) {
                return TransportedResult.doNothing();
            }

            List<ItemStack> results = applyRecipe(level, stack, tool, recipe);

            success[0] = true;

            ItemStack left = stack.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : results) {
                if (result.isEmpty()) {
                    continue;
                }
                TransportedItemStack copy = item.copy();
                copy.stack = result;
                outputs.add(copy);
            }

            if (outputs.isEmpty()) {
                if (left.isEmpty()) {
                    return TransportedResult.removeItem();
                }
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

        if (success[0]) {
            depot.notifyUpdate();
            depot.sendData();
            consumeTool(player, tool, recipe);

            ItemStack pointer = player.getMainHandItem();
            if (pointer.getItem() instanceof PointerItem) {
                pointer.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            playProcessEffects(level, pos, particleItem);
        }
    }

    // ================= useOn =================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                HighlightBlockPacket packet = new HighlightBlockPacket(pos);
                for (ServerPlayer p : serverLevel.getChunkSource().chunkMap
                        .getPlayers(ChunkPos.containing(pos), false)) {
                    ServerPlayNetworking.send(p, packet);
                }
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            ItemStack tool = player.getOffhandItem();
            if (tool.isEmpty()) {
                return InteractionResult.PASS;
            }

            if (tryProcessBelt(player, level, pos, tool)) {
                return InteractionResult.SUCCESS_SERVER;
            }
        }

        return InteractionResult.PASS;
    }

    // ================= 传送带加工 =================

    private static boolean tryProcessBelt(Player player, Level level, BlockPos pos, ItemStack tool) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return false;
        }

        ItemStack[] particleItem = {ItemStack.EMPTY};
        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) {
                return TransportedResult.doNothing();
            }
            ItemStack held = item.stack;
            if (held.isEmpty()) {
                return TransportedResult.doNothing();
            }

            RecipeHolder<?> recipe = findRecipe(level, held, tool);
            if (recipe == null) {
                return TransportedResult.doNothing();
            }

            List<ItemStack> results = applyRecipe(level, held, tool, recipe);

            success[0] = true;
            particleItem[0] = held.copy();

            consumeTool(player, tool, recipe);

            ItemStack left = held.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : results) {
                if (result.isEmpty()) {
                    continue;
                }
                TransportedItemStack copy = item.copy();
                copy.stack = result;
                outputs.add(copy);
            }

            if (outputs.isEmpty()) {
                if (left.isEmpty()) {
                    return TransportedResult.removeItem();
                }
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

        if (success[0]) {
            playProcessEffects(level, pos, particleItem[0]);

            ItemStack pointer = player.getMainHandItem();
            if (pointer.getItem() instanceof PointerItem) {
                pointer.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
        return success[0];
    }

    // ================= 配方查找 =================

    @Nullable
    private static RecipeHolder<?> findRecipe(Level level, ItemStack target, ItemStack tool) {
        ItemApplicationInput input = new ItemApplicationInput(target, tool);

        // 候选集由统一配方池提供。
        //
        // 这里**不使用 mergeInGlobalOrder**：POINTER 的两个类别语义互斥，
        // 优先级是固定的「先 DEPLOYING、后 ITEM_APPLICATION」，而不是管理器混合顺序。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.POINTER, level)) {
            // L3 独占配方（应用家族）：只认归属指杆的那些。
            if (holder.value() instanceof HandMadeApplicationRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.POINTER) {
                    continue;
                }
                if (!exclusive.matches(input, level)) {
                    continue;
                }
                if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
                    continue;
                }
                return holder;
            }

            if (!(holder.value() instanceof ItemApplicationRecipe itemApplication)) {
                continue;
            }
            if (!itemApplication.matches(input, level)) {
                continue;
            }
            // automation 过滤保留在调用方：配方池与 JEI 都不过滤这些“仅手动”配方。
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
                continue;
            }
            return holder;
        }

        return null;
    }

    /** 用 Create 的滚动式配方应用器产出结果。 */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> applyRecipe(Level level, ItemStack target, ItemStack tool,
                                               RecipeHolder<?> recipe) {
        if (!(recipe.value() instanceof CreateRollableRecipe<?> rollable)) {
            return List.of();
        }
        ItemApplicationInput input = new ItemApplicationInput(target.copyWithCount(1), tool);
        return RecipeApplier.applyRecipeOn(level.getRandom(), 1, input,
                (CreateRollableRecipe<ItemApplicationInput>) rollable);
    }

    // ================= 辅助 =================

    private static void consumeTool(Player player, ItemStack tool, RecipeHolder<?> recipe) {
        // L3 独占配方（应用家族）与 Create 的 ItemApplicationRecipe 都可能有“手持物品不消耗”
        if (recipe.value() instanceof HandMadeApplicationRecipe handmade && handmade.keepHeldItem()) {
            return;
        }
        if (recipe.value() instanceof ItemApplicationRecipe ia && ia.keepHeldItem()) {
            return;
        }
        if (tool.getMaxDamage() > 0) {
            tool.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        } else {
            tool.shrink(1);
        }
    }

    private static void playProcessEffects(Level level, BlockPos pos, ItemStack particleItem) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (particleItem.isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PressParticlesPacket packet = new PressParticlesPacket(
                pos, particleItem, ParticleStyle.DEPLOY);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ServerPlayNetworking.send(p, packet);
            }
        }
    }
}

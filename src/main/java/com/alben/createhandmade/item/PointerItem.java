package com.alben.createhandmade.item;

import com.alben.createhandmade.network.HighlightBlockPacket;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.HandMadeApplicationRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber
public class PointerItem extends Item {

    public PointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 22;
    }

    // ================= 左键攻击 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PointerItem) {
            mainHand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PointerItem) {
            offHand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        }
    }

    // ================= 右键放置方块 =================

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PointerItem) {
            mainHand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PointerItem) {
            offHand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        }
    }

    // ★ 已删除 onBreakBlock —— 挖掘耐久由 TOOL 组件自动处理

    // ================= 置物台拦截 =================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;
        if (player.isShiftKeyDown()) return;

        ItemStack tool = player.getOffhandItem();
        if (tool.isEmpty()) return;

        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot)) return;
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return;

        RecipeHolder<?> recipe = findRecipe(level, held, tool);
        if (recipe == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!level.isClientSide) {
            executeDepotProcess(player, level, pos, tool, held, recipe, depot);
        }
    }

    // ================= 置物台加工 =================

    private static void executeDepotProcess(Player player, Level level, BlockPos pos,
                                            ItemStack tool, ItemStack held, RecipeHolder<?> recipe,
                                            DepotBlockEntity depot) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return;

        ItemStack particleItem = held.copy();
        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, stack.copyWithCount(1), recipe.value(), true);

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

        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                PacketDistributor.sendToPlayersTrackingChunk(
                        serverLevel,
                        new ChunkPos(pos),
                        new HighlightBlockPacket(pos)
                );
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            ItemStack tool = player.getOffhandItem();
            if (tool.isEmpty()) return InteractionResult.PASS;

            if (tryProcessBelt(player, level, pos, tool)) return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ================= 传送带加工 =================

    private static boolean tryProcessBelt(Player player, Level level, BlockPos pos, ItemStack tool) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        ItemStack[] particleItem = {ItemStack.EMPTY};
        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack held = item.stack;
            if (held.isEmpty()) return TransportedResult.doNothing();

            RecipeHolder<?> recipe = findRecipe(level, held, tool);
            if (recipe == null) return TransportedResult.doNothing();

            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, held.copyWithCount(1), recipe.value(), true);

            success[0] = true;
            particleItem[0] = held.copy();

            consumeTool(player, tool, recipe);

            ItemStack left = held.copy();
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
        ItemStackHandler tempInv = new ItemStackHandler(2);
        tempInv.setStackInSlot(0, target);
        tempInv.setStackInSlot(1, tool);
        RecipeWrapper wrapper = new RecipeWrapper(tempInv);

        // ★ 序列组装不走配方池：它需要按「输入 + 中间物品」的组装进度解析，
        //   不是一条普通的 DEPLOYING 配方，保持原样。
        Optional<RecipeHolder<DeployerApplicationRecipe>> sequenced =
                SequencedAssemblyRecipe.getRecipe(level, wrapper,
                        AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (sequenced.isPresent()) return sequenced.get();

        // 候选集改由统一配方池提供。
        //
        // ★ 这里**不使用 mergeInGlobalOrder**：POINTER 的两个类别语义互斥，
        //   优先级是固定的「先 DEPLOYING、后 ITEM_APPLICATION」，
        //   而不是管理器混合顺序。配方池返回的列表已经保证
        //   「所有 DEPLOYING 在前、所有 ITEM_APPLICATION 在后，且每个类别内部按管理器顺序」，
        //   所以顺序遍历取第一个匹配即可，与原逻辑一致。
        //
        //   匹配与 automation 过滤留在本类。ItemApplicationRecipe 继承自
        //   ProcessingRecipe<RecipeWrapper, ...>，其 matches 参数正是 RecipeWrapper，无需转换。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.POINTER, level)) {
            // L3 独占配方（应用家族）：只认归属指杆的那些。
            // 它与 Create 的 ItemApplicationRecipe 一样是 2 槽语义（槽 0 目标、槽 1 手持），
            // 但配方类不同，所以要单独判一次。
            if (holder.value() instanceof HandMadeApplicationRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.POINTER) continue;
                if (!exclusive.matches(wrapper, level)) continue;
                if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) continue;
                return holder;
            }

            if (!(holder.value() instanceof ItemApplicationRecipe itemApplication)) continue;
            if (!itemApplication.matches(wrapper, level)) continue;
            // ★ automation 过滤保留在调用方：配方池与 JEI 都不过滤这些"仅手动"配方。
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) continue;
            return holder;
        }

        return null;
    }

    // ================= 辅助 =================

    private static void consumeTool(Player player, ItemStack tool, RecipeHolder<?> recipe) {
        // L3 独占配方（应用家族）与 Create 的 ItemApplicationRecipe 都可能有"手持物品不消耗"
        if (recipe.value() instanceof HandMadeApplicationRecipe handmade && handmade.shouldKeepHeldItem()) {
            return;
        }
        if (recipe.value() instanceof ItemApplicationRecipe ia && ia.shouldKeepHeldItem()) {
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

        if (particleItem.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        PressParticlesPacket packet = new PressParticlesPacket(
                pos, particleItem, ParticleStyle.DEPLOY);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }
}
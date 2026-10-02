package com.alben.createhandmade.item;

import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.network.HighlightBlockPacket;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber
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
            mainHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PointerItem) {
            offHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
        }
    }

    // ================= 右键放置方块 =================

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PointerItem) {
            mainHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PointerItem) {
            offHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
        }
    }

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

        ItemApplicationRecipe recipe = findRecipe(level, held, tool);
        if (recipe == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!level.isClientSide) {
            executeDepotProcess(player, level, pos, tool, held, recipe, depot);
        }
    }

    // ================= 置物台加工 =================

    private static void executeDepotProcess(Player player, Level level, BlockPos pos,
                                            ItemStack tool, ItemStack held, ItemApplicationRecipe recipe,
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
                    level, stack.copyWithCount(1), recipe, true);

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
                pointer.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
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
                HighlightBlockPacket packet = new HighlightBlockPacket(pos);
                ModNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(pos)),
                packet);

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

            ItemApplicationRecipe recipe = findRecipe(level, held, tool);
            if (recipe == null) return TransportedResult.doNothing();

            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, held.copyWithCount(1), recipe, true);

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
                pointer.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            }
        }
        return success[0];
    }

    // ================= 配方查找 =================

    @Nullable
    private static ItemApplicationRecipe findRecipe(Level level, ItemStack target, ItemStack tool) {
        ItemStackHandler tempInv = new ItemStackHandler(2);
        tempInv.setStackInSlot(0, target);
        tempInv.setStackInSlot(1, tool);
        RecipeWrapper wrapper = new RecipeWrapper(tempInv);

        Optional<DeployerApplicationRecipe> sequenced =
                SequencedAssemblyRecipe.getRecipe(level, wrapper,
                        AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (sequenced.isPresent()) return sequenced.get();

        var deploying = AllRecipeTypes.DEPLOYING.find(wrapper, level)
        .filter(AllRecipeTypes.CAN_BE_AUTOMATED)
        .map(r -> (ItemApplicationRecipe) r);
        if (deploying.isPresent()) return deploying.get();

        var itemApp = AllRecipeTypes.ITEM_APPLICATION.find(wrapper, level)
        .filter(AllRecipeTypes.CAN_BE_AUTOMATED)
        .map(r -> (ItemApplicationRecipe) r);
        if (itemApp.isPresent()) return itemApp.get();

        return null;
    }

    // ================= 辅助 =================

    private static void consumeTool(Player player, ItemStack tool, ItemApplicationRecipe recipe) {
        if (recipe.shouldKeepHeldItem()) {
            return;
        }
        if (tool.getMaxDamage() > 0) {
            tool.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
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
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }
}
package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.network.HighlightBlockPacket;
import com.alben.createhandmade.network.ModNetwork;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

public class PointerLiquidMarker {

    public static void doMark(Player player, ServerLevel level, BlockPos pos) {
        MaidDebug.log("LiquidMarker.doMark START: pos={}", pos);

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            MaidDebug.log("LiquidMarker.doMark: be NULL");
            return;
        }

        boolean isBasin = be instanceof BasinBlockEntity;
        boolean isDepot = be instanceof DepotBlockEntity;

        MaidDebug.log("LiquidMarker.doMark: be={}, isBasin={}, isDepot={}",
                be.getClass().getSimpleName(), isBasin, isDepot);

        BlockPos inputPos = PointerDataHelper.getLiquidInput(player, level);
        BlockPos outputPos = PointerDataHelper.getLiquidOutput(player, level);
        BlockPos overflowPos = PointerDataHelper.getLiquidOverflow(player, level);

        MaidDebug.log("LiquidMarker.doMark: input={}, output={}, overflow={}",
                inputPos, outputPos, overflowPos);

        if (inputPos == null) {
            if (!isBasin) {
                MaidDebug.log("LiquidMarker.doMark: reject input, not basin");
                return;
            }
            PointerDataHelper.setLiquidInput(player, level, pos);
            sendHighlight(level, pos, HighlightBlockPacket.COLOR_INPUT);
            MaidDebug.log("LiquidMarker.doMark: set INPUT");
        } else if (outputPos == null) {
            if (!isBasin && !isDepot) {
                MaidDebug.log("LiquidMarker.doMark: reject output, not basin/depot");
                return;
            }
            PointerDataHelper.setLiquidOutput(player, level, pos);
            sendHighlight(level, pos, HighlightBlockPacket.COLOR_OUTPUT);
            MaidDebug.log("LiquidMarker.doMark: set OUTPUT");
        } else if (overflowPos == null) {
            if (!isBasin) {
                MaidDebug.log("LiquidMarker.doMark: reject overflow, not basin");
                return;
            }
            PointerDataHelper.setLiquidOverflow(player, level, pos);
            sendHighlight(level, pos, HighlightBlockPacket.COLOR_OUTPUT);
            MaidDebug.log("LiquidMarker.doMark: set OVERFLOW");
        } else {
            if (!isBasin) {
                MaidDebug.log("LiquidMarker.doMark: reject reset, not basin");
                return;
            }
            PointerDataHelper.setLiquidInput(player, level, pos);
            PointerDataHelper.clearLiquidPartial(player);
            sendHighlight(level, pos, HighlightBlockPacket.COLOR_INPUT);
            MaidDebug.log("LiquidMarker.doMark: reset to INPUT");
        }

        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (event.getSlot() != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return;

        if (event.getTo().getItem() instanceof PointerItem) return;
        if (!(event.getFrom().getItem() instanceof PointerItem)) return;

        PointerDataHelper.clearLiquid(player);
    }

    private static void sendHighlight(ServerLevel level, BlockPos pos, int color) {
        HighlightBlockPacket packet = new HighlightBlockPacket(pos, color);
        ModNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                packet);
    }
}
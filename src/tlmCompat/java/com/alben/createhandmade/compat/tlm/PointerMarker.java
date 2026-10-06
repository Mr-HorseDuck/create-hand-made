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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 指杆标记系统（仅模式 2）。
 *
 * 客户端拦截在 PointerModeHandler.onMouseButton 里处理，
 * 这里只提供服务端 doMark 和切换物品清除标记的逻辑。
 */
@Mod.EventBusSubscriber(modid = "create_hand_made")
public class PointerMarker {

    /**
     * 服务端标记逻辑。由 MaidNetwork.handlePointerMarker 调用。
     */
    public static void doMark(Player player, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        boolean isWorkBlock = be instanceof DepotBlockEntity || be instanceof BasinBlockEntity;
        boolean isContainer = !isWorkBlock
                && be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();

        if (!isWorkBlock && !isContainer) return;

        if (isWorkBlock) {
            PointerDataHelper.setWork(player, level, pos);
            sendHighlight(level, pos, HighlightBlockPacket.COLOR_WORK);
        } else {
            BlockPos inputPos = PointerDataHelper.getInput(player, level);
            BlockPos outputPos = PointerDataHelper.getOutput(player, level);

            if (inputPos == null) {
                PointerDataHelper.setInput(player, level, pos);
                sendHighlight(level, pos, HighlightBlockPacket.COLOR_INPUT);
            } else if (outputPos == null) {
                PointerDataHelper.setOutput(player, level, pos);
                sendHighlight(level, pos, HighlightBlockPacket.COLOR_OUTPUT);
            } else {
                PointerDataHelper.setInput(player, level, pos);
                PointerDataHelper.setOutput(player, level, null);
                sendHighlight(level, pos, HighlightBlockPacket.COLOR_INPUT);
            }
        }

        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 切换物品时清除玩家 NBT 标记。
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (event.getSlot() != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return;

        if (event.getTo().getItem() instanceof PointerItem) return;
        if (!(event.getFrom().getItem() instanceof PointerItem)) return;

        PointerDataHelper.clear(player);
    }

    private static void sendHighlight(ServerLevel level, BlockPos pos, int color) {
        HighlightBlockPacket packet = new HighlightBlockPacket(pos, color);
        ModNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                packet);
    }
}
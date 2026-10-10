package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.PointerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class MaidNetwork {

    private static final String VERSION = "1";
    private static boolean initialized = false;

    public static SimpleChannel CHANNEL;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("create_hand_made", "maid_network"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        int id = 0;

        CHANNEL.messageBuilder(PointerModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PointerModePacket::encode)
                .decoder(PointerModePacket::new)
                .consumerMainThread(MaidNetwork::handlePointerMode)
                .add();

        CHANNEL.messageBuilder(PointerMarkerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PointerMarkerPacket::encode)
                .decoder(PointerMarkerPacket::new)
                .consumerMainThread(MaidNetwork::handlePointerMarker)
                .add();

        CHANNEL.messageBuilder(PointerLiquidMarkerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PointerLiquidMarkerPacket::encode)
                .decoder(PointerLiquidMarkerPacket::new)
                .consumerMainThread(MaidNetwork::handlePointerLiquidMarker)
                .add();

        CHANNEL.messageBuilder(PointerApplyToMaidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PointerApplyToMaidPacket::encode)
                .decoder(PointerApplyToMaidPacket::new)
                .consumerMainThread(MaidNetwork::handleApplyToMaid)
                .add();

        CreateHandMade.LOGGER.info("[HandMade] Maid network registered");
    }

    private static void handlePointerMode(PointerModePacket packet,
                                          Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            MaidDebug.log("handlePointerMode received: mode={}", packet.mode());
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PointerItem)) {
                MaidDebug.log("handlePointerMode: main hand not pointer");
                return;
            }
            PointerModeHelper.setMode(stack, packet.mode());
            MaidDebug.log("handlePointerMode: set mode={}, now={}",
                    packet.mode(), PointerModeHelper.getMode(stack));
        });
        context.setPacketHandled(true);
    }

    private static void handlePointerMarker(PointerMarkerPacket packet,
                                            Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            PointerMarker.doMark(player, serverLevel, packet.pos());
        });
        context.setPacketHandled(true);
    }

    private static void handlePointerLiquidMarker(PointerLiquidMarkerPacket packet,
                                                  Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            MaidDebug.log("handlePointerLiquidMarker received: pos={}", packet.pos());
            ServerPlayer player = context.getSender();
            if (player == null) {
                MaidDebug.log("handlePointerLiquidMarker: player NULL");
                return;
            }
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                MaidDebug.log("handlePointerLiquidMarker: not ServerLevel");
                return;
            }
            PointerLiquidMarker.doMark(player, serverLevel, packet.pos());
        });
        context.setPacketHandled(true);
    }

    private static void handleApplyToMaid(PointerApplyToMaidPacket packet,
                                          Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                if (!(player.level() instanceof ServerLevel serverLevel)) return;

                Entity target = serverLevel.getEntity(packet.entityId());
                if (!(target instanceof EntityMaid)) {
                    MaidDebug.log("ApplyToMaid: target not maid");
                    return;
                }

                Entity maidEntity = target;

                int mode = PointerModeHelper.getMode(player.getMainHandItem());
                MaidDebug.log("ApplyToMaid: mode={}, mainHand={}",
                        mode, player.getMainHandItem().getItem());

                if (mode == PointerModeHelper.MODE_LIQUID) {
                    BlockPos li = PointerDataHelper.getLiquidInput(player, serverLevel);
                    BlockPos lo = PointerDataHelper.getLiquidOutput(player, serverLevel);
                    BlockPos lov = PointerDataHelper.getLiquidOverflow(player, serverLevel);

                    MaidDebug.log("ApplyToMaid LIQUID: input={}, output={}, overflow={}",
                            li, lo, lov);

                    if (li == null || lo == null || lov == null) {
                        player.displayClientMessage(
                                Component.translatable("message.create_hand_made.pointer.incomplete"),
                                true);
                        return;
                    }

                    PointerDataHelper.copyFrom(player, maidEntity);

                    MaidDebug.log("ApplyToMaid LIQUID: applied to maid {}",
                            maidEntity.getId());

                    player.displayClientMessage(
                            Component.translatable("message.create_hand_made.pointer.applied.liquid",
                                    formatPos(li), formatPos(lo), formatPos(lov)),
                            true);
                } else {
                    BlockPos work = PointerDataHelper.getWork(player, serverLevel);
                    BlockPos input = PointerDataHelper.getInput(player, serverLevel);
                    BlockPos output = PointerDataHelper.getOutput(player, serverLevel);

                    MaidDebug.log("ApplyToMaid MARK: work={}, input={}, output={}",
                            work, input, output);

                    if (work == null || input == null || output == null) {
                        player.displayClientMessage(
                                Component.translatable("message.create_hand_made.pointer.incomplete"),
                                true);
                        return;
                    }

                    PointerDataHelper.copyFrom(player, maidEntity);

                    MaidDebug.log("ApplyToMaid MARK: applied to maid {}",
                            maidEntity.getId());

                    player.displayClientMessage(
                            Component.translatable("message.create_hand_made.pointer.applied",
                                    formatPos(work), formatPos(input), formatPos(output)),
                            true);
                }

                serverLevel.playSound(null, maidEntity.blockPosition(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 1.0F, 1.0F);

            } catch (Throwable t) {
                CreateHandMade.LOGGER.error("[HandMade] ApplyToMaid error", t);
            }
        });
        context.setPacketHandled(true);
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
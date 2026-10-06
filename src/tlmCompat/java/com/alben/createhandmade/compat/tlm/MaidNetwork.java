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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "create_hand_made", bus = Mod.EventBusSubscriber.Bus.MOD)
public class MaidNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("create_hand_made", "maid_network"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
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
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PointerItem)) return;
            PointerModeHelper.setMode(stack, packet.mode());
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
                    CreateHandMade.LOGGER.info("[HandMade] ApplyToMaid: target not maid");
                    return;
                }

                // ★ 直接用 Entity 类型，避免任何 EntityMaid 方法覆写问题
                Entity maidEntity = target;

                BlockPos work = PointerDataHelper.getWork(player, serverLevel);
                BlockPos input = PointerDataHelper.getInput(player, serverLevel);
                BlockPos output = PointerDataHelper.getOutput(player, serverLevel);

                if (work == null || input == null || output == null) {
                    player.displayClientMessage(
                            Component.translatable("message.create_hand_made.pointer.incomplete"),
                            true);
                    return;
                }

                PointerDataHelper.copyFrom(player, maidEntity);

                CreateHandMade.LOGGER.info("[HandMade] Applied marks to maid {}",
                        maidEntity.getId());

                player.displayClientMessage(
                        Component.translatable("message.create_hand_made.pointer.applied",
                                formatPos(work), formatPos(input), formatPos(output)),
                        true);

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
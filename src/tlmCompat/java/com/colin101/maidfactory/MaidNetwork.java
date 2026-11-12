package com.colin101.maidfactory;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.PointerItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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

    /** 给女仆套用标记的距离上限（平方）；指杆伸手范围 5 格，留 8 格余量。 */
    private static final double MAID_APPLY_REACH_SQR = 64.0D;

    public static SimpleChannel CHANNEL;

    public static synchronized void init() {
        if (initialized) return;

        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("create_hand_made", "maid_network"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals);

        int id = 0;

        CHANNEL.messageBuilder(MaidNetwork.ModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MaidNetwork.ModePacket::encode)
                .decoder(MaidNetwork.ModePacket::new)
                .consumerMainThread(MaidNetwork::handlePointerMode)
                .add();

        CHANNEL.messageBuilder(MaidNetwork.MarkerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MaidNetwork.MarkerPacket::encode)
                .decoder(MaidNetwork.MarkerPacket::new)
                .consumerMainThread(MaidNetwork::handlePointerMarker)
                .add();

        CHANNEL.messageBuilder(MaidNetwork.LiquidMarkerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MaidNetwork.LiquidMarkerPacket::encode)
                .decoder(MaidNetwork.LiquidMarkerPacket::new)
                .consumerMainThread(MaidNetwork::handlePointerLiquidMarker)
                .add();

        CHANNEL.messageBuilder(MaidNetwork.ApplyToMaidPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MaidNetwork.ApplyToMaidPacket::encode)
                .decoder(MaidNetwork.ApplyToMaidPacket::new)
                .consumerMainThread(MaidNetwork::handleApplyToMaid)
                .add();

        CreateHandMade.LOGGER.info("[HandMade] Maid network registered");
        initialized = true;
    }

    private static void handlePointerMode(MaidNetwork.ModePacket packet,
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
            PointerDataHelper.Mode.setMode(stack, packet.mode());
            MaidDebug.log("handlePointerMode: set mode={}, now={}",
                    packet.mode(), PointerDataHelper.Mode.getMode(stack));
        });
        context.setPacketHandled(true);
    }

    private static void handlePointerMarker(MaidNetwork.MarkerPacket packet,
                                            Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            // ★ 坐标由客户端提供，服务端必须自己核一遍：手持指杆 + 够得着
            if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
                MaidDebug.log("handlePointerMarker: main hand not pointer, ignore");
                return;
            }
            if (!PointerMarker.isWithinReach(player, packet.pos())) {
                MaidDebug.log("handlePointerMarker: pos {} out of reach from {}",
                        packet.pos(), player.blockPosition());
                return;
            }

            PointerMarker.doMark(player, serverLevel, packet.pos());
        });
        context.setPacketHandled(true);
    }

    private static void handlePointerLiquidMarker(MaidNetwork.LiquidMarkerPacket packet,
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

            // ★ 同 handlePointerMarker：手持指杆 + 够得着
            if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
                MaidDebug.log("handlePointerLiquidMarker: main hand not pointer, ignore");
                return;
            }
            if (!PointerMarker.isWithinReach(player, packet.pos())) {
                MaidDebug.log("handlePointerLiquidMarker: pos {} out of reach from {}",
                        packet.pos(), player.blockPosition());
                return;
            }

            PointerLiquidMarker.doMark(player, serverLevel, packet.pos());
        });
        context.setPacketHandled(true);
    }

    private static void handleApplyToMaid(MaidNetwork.ApplyToMaidPacket packet,
                                          Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                if (!(player.level() instanceof ServerLevel serverLevel)) return;

                // ★ 这个包会直接写女仆的标记数据，必须自己核：手持指杆 + 是自己的女仆 + 够得着。
                //   之前只按 entityId 找实体，改过的客户端能给任意人的女仆写标记。
                if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
                    MaidDebug.log("ApplyToMaid: main hand not pointer, ignore");
                    return;
                }

                Entity target = serverLevel.getEntity(packet.entityId());
                if (!(target instanceof EntityMaid maid)) {
                    MaidDebug.log("ApplyToMaid: target not maid");
                    return;
                }
                if (!maid.isAlive() || !maid.isOwnedBy(player)) {
                    MaidDebug.log("ApplyToMaid: maid {} is not sender's", maid.getId());
                    return;
                }
                if (player.distanceToSqr(maid) > MAID_APPLY_REACH_SQR) {
                    MaidDebug.log("ApplyToMaid: maid {} out of reach from {}",
                            maid.getId(), player.blockPosition());
                    return;
                }

                Entity maidEntity = maid;

                int mode = PointerDataHelper.Mode.getMode(player.getMainHandItem());
                MaidDebug.log("ApplyToMaid: mode={}, mainHand={}",
                        mode, player.getMainHandItem().getItem());

                if (mode == PointerDataHelper.Mode.MODE_LIQUID) {
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

                    // ★ 只有工作方块是必需的：只标工作方块 = 只加工模式
                    //   （女仆只处理玩家自己放在工作方块上的物品，不取料、不搬产物）。
                    if (work == null) {
                        player.displayClientMessage(
                                Component.translatable("message.create_hand_made.pointer.incomplete"),
                                true);
                        return;
                    }

                    PointerDataHelper.copyFrom(player, maidEntity);

                    MaidDebug.log("ApplyToMaid MARK: applied to maid {}",
                            maidEntity.getId());

                    if (input != null && output != null) {
                        player.displayClientMessage(
                                Component.translatable("message.create_hand_made.pointer.applied",
                                        formatPos(work), formatPos(input), formatPos(output)),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.create_hand_made.pointer.applied.workonly",
                                        formatPos(work)),
                                true);
                    }
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

    // ==================== 网络包 ====================
    // 四个包都只在这一处注册、只跟指杆有关，所以直接嵌在这里，不再各占一个文件。

    /** 客户端 → 服务端：切换指杆模式。 */
    public static class ModePacket {

        private final int mode;

        public ModePacket(int mode) {
            this.mode = mode;
        }

        public ModePacket(FriendlyByteBuf buf) {
            this.mode = buf.readInt();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(mode);
        }

        public int mode() {
            return mode;
        }
    }

    /** 客户端 → 服务端：工作方块标记（MODE_MARK）。 */
    public static class MarkerPacket {

        private final BlockPos pos;

        public MarkerPacket(BlockPos pos) {
            this.pos = pos;
        }

        public MarkerPacket(FriendlyByteBuf buf) {
            this.pos = buf.readBlockPos();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public BlockPos pos() {
            return pos;
        }
    }

    /**
     * 客户端 → 服务端：液体模式标记包（MODE_LIQUID）。
     * ★ 与 {@link MarkerPacket} 结构相同，只传 BlockPos；服务端 handler 走 PointerLiquidMarker.doMark。
     */
    public static class LiquidMarkerPacket {

        private final BlockPos pos;

        public LiquidMarkerPacket(BlockPos pos) {
            this.pos = pos;
        }

        public LiquidMarkerPacket(FriendlyByteBuf buf) {
            this.pos = buf.readBlockPos();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public BlockPos pos() {
            return pos;
        }
    }

    /** 客户端 → 服务端：把当前指针数据套用到某个女仆身上。 */
    public static class ApplyToMaidPacket {

        private final int entityId;

        public ApplyToMaidPacket(int entityId) {
            this.entityId = entityId;
        }

        public ApplyToMaidPacket(FriendlyByteBuf buf) {
            this.entityId = buf.readInt();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(entityId);
        }

        public int entityId() {
            return entityId;
        }
    }
}
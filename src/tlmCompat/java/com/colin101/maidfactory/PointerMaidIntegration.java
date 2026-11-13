package com.colin101.maidfactory;

import com.alben.createhandmade.item.PointerItem;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 潜行右键女仆应用标记 —— TLM 交互事件路径。
 *
 * <p>★ 与 {@code MaidNetwork.handleApplyToMaid}（客户端发的 ApplyToMaidPacket）分工：</p>
 * <ul>
 *   <li>本类：默认 / MARK 模式。借 TLM 的 {@link InteractMaidEvent}，优点是不依赖客户端
 *       发包、也不会被 TLM 自己的交互逻辑吃掉。</li>
 *   <li>Packet 路径：液体模式，以及客户端主动发包的场景。服务端侧会做
 *       手持指杆 + 是自己女仆 + 距离校验。</li>
 * </ul>
 *
 * <p>两条路径都会写同一份女仆标记，所以**校验条件必须保持一致**：液体模式在下面直接
 * return（交给 Packet 路径），避免这里拿 MARK 的坐标去校验、弹出「标记不完整」误报。
 * 将来若合并成一条路径，记得把这里的 TLM 事件豁免一起处理掉。</p>
 */
public class PointerMaidIntegration {

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();

        if (!player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;

        // ★ 强转 Entity，避免 TLM 版本间方法签名变化导致 NoSuchMethodError
        Entity entity = (Entity) maid;
        if (entity.level().isClientSide) return;

        // ★ 两类标记互斥：液体模式走 MaidNetwork 的 ApplyToMaidPacket 那条路径，
        //   这里只管默认/MARK 模式。否则液体模式下会拿 MARK 的坐标去校验，
        //   弹出「标记不完整」的误报（而且还会把液体坐标裁掉）。
        int mode = PointerDataHelper.Mode.getMode(player.getMainHandItem());
        if (mode == PointerDataHelper.Mode.MODE_LIQUID) return;

        BlockPos work = PointerDataHelper.getWork(player, entity.level());
        BlockPos input = PointerDataHelper.getInput(player, entity.level());
        BlockPos output = PointerDataHelper.getOutput(player, entity.level());

        // ★ 只有工作方块是必需的：只标工作方块 = 只加工模式
        //   （女仆只处理玩家自己放在工作方块上的物品，不取料、不搬产物）。
        if (work == null) {
            player.displayClientMessage(
                    Component.translatable("message.create_hand_made.pointer.incomplete")
                            .withStyle(ChatFormatting.RED),
                    true);
            event.setCanceled(true);
            return;
        }

        PointerDataHelper.copyFrom(player, maid);

        Component feedback = (input != null && output != null)
                ? Component.translatable("message.create_hand_made.pointer.applied",
                        formatPos(work), formatPos(input), formatPos(output))
                : Component.translatable("message.create_hand_made.pointer.applied.workonly",
                        formatPos(work));

        player.displayClientMessage(feedback.copy().withStyle(ChatFormatting.GREEN), true);

        entity.level().playSound(null, entity.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        event.setCanceled(true);
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
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
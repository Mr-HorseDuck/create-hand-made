package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.item.PointerDataHelper;
import com.alben.createhandmade.item.PointerItem;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "create_hand_made")
public class PointerMaidIntegration {

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();

        if (!player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;
        if (maid.level().isClientSide) return;

        BlockPos work = PointerDataHelper.getWork(player, maid.level());
        BlockPos input = PointerDataHelper.getInput(player, maid.level());
        BlockPos output = PointerDataHelper.getOutput(player, maid.level());

        if (work == null || input == null || output == null) {
            player.displayClientMessage(
                    Component.translatable("message.create_hand_made.pointer.incomplete")
                            .withStyle(ChatFormatting.RED),
                    true);
            event.setCanceled(true);
            return;
        }

        PointerDataHelper.copyFrom(player, maid);

        player.displayClientMessage(
                Component.translatable("message.create_hand_made.pointer.applied",
                                formatPos(work), formatPos(input), formatPos(output))
                        .withStyle(ChatFormatting.GREEN),
                true);

        maid.level().playSound(null, maid.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        event.setCanceled(true);
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
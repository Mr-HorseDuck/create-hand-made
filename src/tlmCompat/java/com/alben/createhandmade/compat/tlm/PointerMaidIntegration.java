package com.alben.createhandmade.compat.tlm;

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
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "create_hand_made")
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

        entity.level().playSound(null, entity.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);

        event.setCanceled(true);
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
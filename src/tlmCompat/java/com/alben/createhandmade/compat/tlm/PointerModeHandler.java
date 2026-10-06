package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.client.PointerHighlightClient;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.network.HighlightBlockPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class PointerModeHandler {

    private static boolean lastShiftRightDown = false;

    private static BlockPos clientWork = null;
    private static BlockPos clientInput = null;
    private static BlockPos clientOutput = null;

    // ================= Ctrl + 滚轮 切换模式 =================

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        long window = mc.getWindow().getWindow();
        boolean ctrlPressed = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (!ctrlPressed) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof PointerItem)) return;

        PointerModeHelper.cycleMode(stack);
        int mode = PointerModeHelper.getMode(stack);

        CreateHandMade.LOGGER.info("[HandMade] Pointer mode cycled to {}", mode);

        player.displayClientMessage(
                Component.translatable("message.create_hand_made.pointer.mode." + mode)
                        .withStyle(ChatFormatting.YELLOW),
                true);

        MaidNetwork.CHANNEL.sendToServer(new PointerModePacket(mode));

        event.setCanceled(true);
    }

    // ================= 每 tick 检测 Shift+右键 边沿 =================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            lastShiftRightDown = false;
            return;
        }

        // 主手不是指杆时清空本地缓存
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
            clientWork = null;
            clientInput = null;
            clientOutput = null;
        }

        long window = mc.getWindow().getWindow();
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean currently = rightDown && player.isShiftKeyDown();

        if (currently && !lastShiftRightDown) {
            handleShiftRightClick(mc, player);
        }

        lastShiftRightDown = currently;
    }

    private static void handleShiftRightClick(Minecraft mc, Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof PointerItem)) return;

        int mode = PointerModeHelper.getMode(mainHand);
        if (mode != PointerModeHelper.MODE_MARK) return;

        HitResult hit = mc.hitResult;
        if (hit == null) return;

        // ★ 命中实体：检查是否为女仆
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            // 用类名判断，避免与 TLM 直接耦合
            if (isMaid(target)) {
                CreateHandMade.LOGGER.info("[HandMade] Apply marks to maid id={}", target.getId());
                MaidNetwork.CHANNEL.sendToServer(new PointerApplyToMaidPacket(target.getId()));
            }
            return;
        }

        // 命中方块：标记逻辑
        if (!(hit instanceof BlockHitResult blockHit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos().immutable();
        if (mc.level == null) return;

        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        boolean isWorkBlock = be instanceof DepotBlockEntity || be instanceof BasinBlockEntity;
        if (!isWorkBlock) {
            // 非工作方块，且无 capability 时忽略
            boolean hasItemHandler = be.getCapability(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).isPresent();
            if (!hasItemHandler) return;
        }

        int color;
        if (isWorkBlock) {
            clientWork = pos;
            color = HighlightBlockPacket.COLOR_WORK;
        } else {
            if (clientInput == null) {
                clientInput = pos;
                color = HighlightBlockPacket.COLOR_INPUT;
            } else if (clientOutput == null) {
                clientOutput = pos;
                color = HighlightBlockPacket.COLOR_OUTPUT;
            } else {
                clientInput = pos;
                clientOutput = null;
                color = HighlightBlockPacket.COLOR_INPUT;
            }
        }

        PointerHighlightClient.setHighlight(color, pos);

        CreateHandMade.LOGGER.info("[HandMade] Marker client: pos={}, color={}", pos, Integer.toHexString(color));

        MaidNetwork.CHANNEL.sendToServer(new PointerMarkerPacket(pos));
    }

    /**
     * 用类名判断是否为 TLM 女仆，避免编译期依赖。
     */
    private static boolean isMaid(Entity entity) {
        return entity.getClass().getName()
                .equals("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
    }

    // ================= 兜底：阻止 use 包发出 =================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) return;
        if (PointerModeHelper.getMode(player.getMainHandItem()) != PointerModeHelper.MODE_MARK) return;

        event.setCanceled(true);
    }
}
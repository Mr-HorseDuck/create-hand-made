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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class PointerModeHandler {

    private static boolean lastShiftRightDown = false;

    // ★ MODE_MARK 的客户端缓存
    private static BlockPos clientWork = null;
    private static BlockPos clientInput = null;
    private static BlockPos clientOutput = null;

    // ★ MODE_LIQUID 的客户端缓存
    private static BlockPos clientLiquidInput = null;
    private static BlockPos clientLiquidOutput = null;
    private static BlockPos clientLiquidOverflow = null;

    // ================= HUD 读取接口 =================

    public static BlockPos getClientLiquidInput() {
        return clientLiquidInput;
    }

    public static BlockPos getClientLiquidOutput() {
        return clientLiquidOutput;
    }

    public static BlockPos getClientLiquidOverflow() {
        return clientLiquidOverflow;
    }

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

        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
            clientWork = null;
            clientInput = null;
            clientOutput = null;
            clientLiquidInput = null;
            clientLiquidOutput = null;
            clientLiquidOverflow = null;
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
        if (mode != PointerModeHelper.MODE_MARK && mode != PointerModeHelper.MODE_LIQUID) return;

        HitResult hit = mc.hitResult;
        if (hit == null) return;

        // ---- 右键女仆：应用标记（两种模式通用） ----
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (isMaid(target)) {
                CreateHandMade.LOGGER.info("[HandMade] Apply marks to maid id={}", target.getId());
                MaidNetwork.CHANNEL.sendToServer(new PointerApplyToMaidPacket(target.getId()));
            }
            return;
        }

        if (!(hit instanceof BlockHitResult blockHit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos().immutable();
        if (mc.level == null) return;

        if (mode == PointerModeHelper.MODE_MARK) {
            handleMarkMode(mc, pos);
        } else {
            handleLiquidMode(mc, pos);
        }
    }

    // ================= MODE_MARK 的标记逻辑（原逻辑不变） =================

    private static void handleMarkMode(Minecraft mc, BlockPos pos) {
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        boolean isWorkBlock = be instanceof DepotBlockEntity || be instanceof BasinBlockEntity;
        if (!isWorkBlock) {
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

    // ================= MODE_LIQUID 的标记逻辑（新增） =================

    /**
     * 液体模式标记三个坐标：
     *   1. 输入（必须是 Basin）
     *   2. 输出（Basin 或 Depot）
     *   3. 过剩输出（必须是 Basin）
     * 标完第三个后，再右键覆盖回输入。
     */
    private static void handleLiquidMode(Minecraft mc, BlockPos pos) {
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        boolean isBasin = be instanceof BasinBlockEntity;
        boolean isDepot = be instanceof DepotBlockEntity;

        int color;
        if (clientLiquidInput == null) {
            // 第 1 个：输入，必须是 Basin
            if (!isBasin) return;
            clientLiquidInput = pos;
            color = HighlightBlockPacket.COLOR_INPUT;
        } else if (clientLiquidOutput == null) {
            // 第 2 个：输出，Basin 或 Depot 都可以
            if (!isBasin && !isDepot) return;
            clientLiquidOutput = pos;
            color = HighlightBlockPacket.COLOR_OUTPUT;
        } else if (clientLiquidOverflow == null) {
            // 第 3 个：过剩输出，必须是 Basin
            if (!isBasin) return;
            clientLiquidOverflow = pos;
            color = HighlightBlockPacket.COLOR_OUTPUT;
        } else {
            // 三个都标完了，再右键重置为输入
            if (!isBasin) return;
            clientLiquidInput = pos;
            clientLiquidOutput = null;
            clientLiquidOverflow = null;
            color = HighlightBlockPacket.COLOR_INPUT;
        }

        PointerHighlightClient.setHighlight(color, pos);

        CreateHandMade.LOGGER.info(
                "[HandMade] Liquid marker client: pos={}, color={}",
                pos, Integer.toHexString(color));

        MaidNetwork.CHANNEL.sendToServer(new PointerLiquidMarkerPacket(pos));
    }

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

        int mode = PointerModeHelper.getMode(player.getMainHandItem());
        if (mode != PointerModeHelper.MODE_MARK && mode != PointerModeHelper.MODE_LIQUID) return;

        event.setCanceled(true);
    }
}
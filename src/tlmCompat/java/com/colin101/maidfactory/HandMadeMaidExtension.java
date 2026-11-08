package com.colin101.maidfactory;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidEquipEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;

@LittleMaidExtension
public class HandMadeMaidExtension implements ILittleMaid {

    private static boolean EVENTS_REGISTERED = false;

    @Override
    public void addMaidTask(TaskManager manager) {
        try {
            if (isBlockedByMod()) {
                CreateHandMade.LOGGER.info(
                        "[HandMade] Maid compatibility disabled: a blocked mod is loaded");
                return;
            }

            if (ModItems.PRESS_HAMMER.get() == null) {
                CreateHandMade.LOGGER.error(
                        "[HandMade] ModItems.PRESS_HAMMER not registered yet, "
                                + "skipping maid task registration");
                return;
            }

            manager.add(new TaskHandMade());

            if (EVENTS_REGISTERED) {
                CreateHandMade.LOGGER.debug(
                        "[HandMade] Maid events already registered, skip");
                return;
            }
            EVENTS_REGISTERED = true;

            IEventBus modEventBus = CreateHandMade.MOD_EVENT_BUS;
            if (modEventBus == null) {
                CreateHandMade.LOGGER.error(
                        "[HandMade] MOD_EVENT_BUS is null, "
                                + "maid event registration skipped this session");
                return;
            }

            MinecraftForge.EVENT_BUS.register(MaidNetwork.class);
            MinecraftForge.EVENT_BUS.register(PointerMarker.class);
            MinecraftForge.EVENT_BUS.register(PointerMaidIntegration.class);
            MinecraftForge.EVENT_BUS.register(HandMadeMaidExtension.class);

            modEventBus.register(MaidNetwork.class);

            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MinecraftForge.EVENT_BUS.register(PointerModeHandler.class);
                MinecraftForge.EVENT_BUS.register(PointerHudRenderer.class);
            });

            CreateHandMade.LOGGER.info("[HandMade] Maid compatibility initialized");

        } catch (Throwable t) {
            CreateHandMade.LOGGER.error(
                    "[HandMade] Failed to register maid task, "
                            + "maid compatibility will be unavailable this session", t);
        }
    }

    private static boolean isBlockedByMod() {
        try {
            // ★ blocked_mods 在 MaidStartupConfig（COMMON）里：addMaidTask 阶段 SERVER 配置还没加载。
            //   万一 COMMON 也还没加载（开发环境 get() 会抛），退回配置默认值，别让屏蔽失效。
            var list = MaidStartupConfig.SPEC.isLoaded()
                    ? MaidStartupConfig.INSTANCE.blockedMods.get()
                    : MaidStartupConfig.INSTANCE.blockedMods.getDefault();
            MaidDebug.log("blockedMods content = {} (size={})", list, list.size());

            for (String modId : list) {
                if (modId == null || modId.isEmpty()) continue;
                boolean loaded = ModList.get().isLoaded(modId);
                MaidDebug.log("checking mod '{}': loaded={}", modId, loaded);
                if (loaded) {
                    CreateHandMade.LOGGER.info(
                            "[HandMade] Blocked by loaded mod: {}", modId);
                    return true;
                }
            }
        } catch (Throwable t) {
            // 这里是异常路径，不受 debugEnabled 控制：配置读崩了必须留痕
            CreateHandMade.LOGGER.warn(
                    "[HandMade] isBlockedByMod failed, assuming not blocked", t);
        }
        return false;
    }

    // ==================== 气泡：装备变更触发首次使用 ====================

    /**
     * 女仆主手装备手搓工具时，触发该工具的首次使用气泡。
     * 首次使用标记存在 NBT，每个女仆每个工具只弹一次。
     * 行为类里的 tryShowFirstUse 作为兜底（女仆出生时就拿着工具、没走装备事件的情况）。
     *
     * <p>这里走 Forge 的 {@link LivingEquipmentChangeEvent}，它是"每 tick 比对装备差异"后
     * 才发的，女仆换手时不一定稳；真正的首选触发点是下面的 {@link MaidEquipEvent}。
     * 两个都注册、靠 NBT 标记 + 冷却去重，重复触发不会弹两次。</p>
     */
    @SubscribeEvent
    public static void onMaidEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;
        if (((Entity) maid).level().isClientSide) return;

        ItemStack newItem = event.getTo();
        ToolType tool = MaidBubbleHelper.toolTypeOf(newItem);
        if (tool == null) return;

        MaidBubbleHelper.tryShowFirstUse(maid, tool, newItem);
    }

    /**
     * 女仆装备变更（TLM 自己的事件）：<b>这才是"第一次拿工具"最可靠的触发点</b>。
     *
     * <p>{@code EntityMaid.setItemSlot} 在服务端同步 post 这个事件，不依赖每 tick 的装备差异比对；
     * Forge 的 {@link LivingEquipmentChangeEvent} 在女仆身上不一定触发，导致装备时的首次气泡
     * 实际上弹不出来（只剩行为类里"第一次成功加工"才补）。</p>
     */
    @SubscribeEvent
    public static void onMaidEquip(MaidEquipEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;
        if (((Entity) maid).level().isClientSide) return;

        ItemStack stack = event.getStack();
        ToolType tool = MaidBubbleHelper.toolTypeOf(stack);
        if (tool == null) return;

        MaidBubbleHelper.tryShowFirstUse(maid, tool, stack);
    }

    // ==================== 气泡缓存清理 ====================

    @SubscribeEvent
    public static void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            MaidBubbleHelper.clear(((Entity) maid).getUUID());
        }
    }

    /**
     * 女仆离开世界（区块卸载、维度切换、玩家下线）时，只清连续判断的状态，
     * 不清 LAST_BUBBLE_TIME。否则女仆跨区块时气泡冷却会被反复重置，变成“一直弹”。
     */
    @SubscribeEvent
    public static void onMaidLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            MaidBubbleHelper.clearContinuousState(((Entity) maid).getUUID());
        }
    }
}
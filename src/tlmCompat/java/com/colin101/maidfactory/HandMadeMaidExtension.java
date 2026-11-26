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

            // ★ 逐个注册、各自兜异常：以前是一条 try 包住全部，任何一项抛异常（比如某个类
            //   引用了 TLM 改过名的 API）会把**后面所有**注册一起跳过 —— 症状就是
            //   「指杆右键女仆没反应 / HUD 不见 / 高亮不动」，而日志里只有一条泛泛的 error。
            registerQuietly(MinecraftForge.EVENT_BUS, MaidNetwork.class);
            registerQuietly(MinecraftForge.EVENT_BUS, PointerMarker.class);
            // ★ 液体模式的「换下手持物就清标记」在 PointerLiquidMarker 里；
            //   以前漏了这一行，导致 clearLiquid() 全库没有调用点（标记永不失效）。
            registerQuietly(MinecraftForge.EVENT_BUS, PointerLiquidMarker.class);
            registerQuietly(MinecraftForge.EVENT_BUS, PointerMaidIntegration.class);
            registerQuietly(MinecraftForge.EVENT_BUS, HandMadeMaidExtension.class);

            registerQuietly(modEventBus, MaidNetwork.class);

            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                registerQuietly(MinecraftForge.EVENT_BUS, PointerModeHandler.class);
                registerQuietly(MinecraftForge.EVENT_BUS, PointerHudRenderer.class);
            });

            CreateHandMade.LOGGER.info("[HandMade] Maid compatibility initialized");

        } catch (Throwable t) {
            CreateHandMade.LOGGER.error(
                    "[HandMade] Failed to register maid task, "
                            + "maid compatibility will be unavailable this session", t);
        }
    }

    /**
     * 与女仆兼容冲突、需要自动禁用女仆兼容的模组。
     *
     * <p>★ 写死在代码里，不开放配置：这类兼容性判定属于模组自己的知识，
     * 交给玩家改 config 只会造成「女仆兼容神神失灵」而又不知道为什么。</p>
     */
    private static final java.util.List<String> BLOCKED_MODS =
            java.util.List.of("maidassemblyline");

    private static boolean isBlockedByMod() {
        try {
            for (String modId : BLOCKED_MODS) {
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
            // 异常路径不受 debugEnabled 控制：判定崩了必须留痕
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

    /**
     * 注册单个类到事件总线，失败只记日志、不影响其它类的注册。
     *
     * <p>为什么需要：这些类都直接引用 TLM 的 API，TLM 大版本改动时某一项可能加载失败；
     * 以前用一条 try 包住全部注册，一处失败会让后面的 PointerModeHandler / PointerHudRenderer
     * 一起丢失，表现为「指杆右键女仆没反应」这类很难定位的症状。</p>
     */
    private static void registerQuietly(IEventBus bus, Class<?> listener) {
        try {
            bus.register(listener);
        } catch (Throwable t) {
            CreateHandMade.LOGGER.error("[HandMade] 注册 {} 到事件总线失败（相关功能将不可用）",
                    listener.getName(), t);
        }
    }
}

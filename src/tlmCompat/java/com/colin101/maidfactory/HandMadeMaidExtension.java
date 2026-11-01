package com.colin101.maidfactory;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
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
            var list = MaidConfig.INSTANCE.blockedMods.get();
            CreateHandMade.LOGGER.info(
                    "[HandMade-Debug] blockedMods content = {} (size={})",
                    list, list.size());

            for (String modId : list) {
                if (modId == null || modId.isEmpty()) continue;
                boolean loaded = ModList.get().isLoaded(modId);
                CreateHandMade.LOGGER.info(
                        "[HandMade-Debug] checking mod '{}': loaded={}",
                        modId, loaded);
                if (loaded) {
                    CreateHandMade.LOGGER.info(
                            "[HandMade] Blocked by loaded mod: {}", modId);
                    return true;
                }
            }
        } catch (Throwable t) {
            CreateHandMade.LOGGER.warn(
                    "[HandMade-Debug] isBlockedByMod exception, assuming not blocked", t);
        }
        return false;
    }
}
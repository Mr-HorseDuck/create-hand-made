package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.CreateHandMade;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@LittleMaidExtension
public class HandMadeMaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        if (isBlockedByMod()) {
            CreateHandMade.LOGGER.info(
                    "[HandMade] Maid compatibility disabled: a blocked mod is loaded");
            return;
        }
        manager.add(new TaskHandMade());
        MaidNetwork.init();

        // ★ 手动注册事件（避免 TLM 未安装时 Forge 自动扫描导致崩溃）
        MinecraftForge.EVENT_BUS.register(MaidNetwork.class);
        MinecraftForge.EVENT_BUS.register(PointerMarker.class);
        MinecraftForge.EVENT_BUS.register(PointerMaidIntegration.class);

        FMLJavaModLoadingContext.get().getModEventBus().register(MaidNetwork.class);

        // 客户端专属
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(PointerModeHandler.class);
            MinecraftForge.EVENT_BUS.register(PointerHudRenderer.class);
            FMLJavaModLoadingContext.get().getModEventBus().register(MaidLangOverride.class);
        });

        CreateHandMade.LOGGER.info("[HandMade] Maid compatibility initialized");
    }

        private static boolean isBlockedByMod() {
        try {
            var list = MaidConfig.INSTANCE.blockedMods.get();
            CreateHandMade.LOGGER.info(
                    "[HandMade-Debug] blockedMods content = {} (size={})",
                    list, list.size());

            for (String modId : list) {
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
        } catch (Exception e) {
            CreateHandMade.LOGGER.warn(
                    "[HandMade-Debug] isBlockedByMod exception", e);
        }
        return false;
    }
}
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
            for (String modId : MaidConfig.INSTANCE.blockedMods.get()) {
                if (ModList.get().isLoaded(modId)) {
                    CreateHandMade.LOGGER.info(
                            "[HandMade] Blocked by loaded mod: {}", modId);
                    return true;
                }
            }
        } catch (Exception e) {
            // 配置未加载时静默跳过
        }
        return false;
    }
}
package com.alben.createhandmade;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * ★ Forge 1.20.1 版本：
 *   - 去掉 @Mod 注解（一个模组只能有一个 @Mod 类，主类是 CreateHandMade）
 *   - 只用 @Mod.EventBusSubscriber 标注客户端专属事件
 *   - 如果确实需要配置屏幕，见下方"可选：配置屏幕"部分
 */
@Mod.EventBusSubscriber(
        modid = CreateHandMade.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class CreateHandMadeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CreateHandMade.LOGGER.info("HELLO FROM CLIENT SETUP");
        CreateHandMade.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
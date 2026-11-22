package com.alben.createhandmade;

import com.alben.createhandmade.client.HandRenderHandlers;
import com.alben.createhandmade.client.config.ConfigScreens;
import com.alben.createhandmade.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * ★ Forge 1.20.1 版本：
 *   - 去掉 @Mod 注解（一个模组只能有一个 @Mod 类，主类是 CreateHandMade）
 *   - 只用 @Mod.EventBusSubscriber 标注客户端专属事件
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

        // ★ 注册 Cloth Config 配置界面（可选依赖）
        registerConfigScreen();
    }

    /**
     * ★ 注册灌注枪液体背景装饰器。
     *
     * <p>IItemDecorator 由 {@code GuiGraphics.renderItemDecorations} 在所有物品渲染位置
     * 统一调用——快捷栏、物品栏、箱子、JEI 全部覆盖。</p>
     *
     * <p>RegisterItemDecorationsEvent 是 IModBusEvent，本类已声明 bus = MOD，
     * 所以 @SubscribeEvent 会自动挂到 modEventBus，不需要手动 addListener。</p>
     */
    @SubscribeEvent
    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.INFUSION_GUN.get(), new HandRenderHandlers.InfusionGunDecorator());
        CreateHandMade.LOGGER.info("[HandMade] Infusion gun fluid decorator registered.");
    }

    /**
     * 注册自绘的配置界面（Forge 的 Mods 列表 → Config 按钮）。
     *
     * <p>0.7.0 起不再用 Cloth Config：界面自己画，见
     * {@link com.alben.createhandmade.client.config.HandMadeConfigScreen}。
     * 所以这里也不再需要「没装 Cloth 就跳过」的守卫，也不需要 {@code Class.forName}。</p>
     */
    private static void registerConfigScreen() {
        ConfigScreenHandler.ConfigScreenFactory factory =
                new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> ConfigScreens.create(parent));
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class, () -> factory);
        CreateHandMade.LOGGER.info("[HandMade] Config screen registered (built-in).");
    }
}
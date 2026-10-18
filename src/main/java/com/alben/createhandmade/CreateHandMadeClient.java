package com.alben.createhandmade;

import com.alben.createhandmade.client.InfusionGunFluidDecorator;
import com.alben.createhandmade.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
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
        event.register(ModItems.INFUSION_GUN.get(), new InfusionGunFluidDecorator());
        CreateHandMade.LOGGER.info("[HandMade] Infusion gun fluid decorator registered.");
    }

    /**
     * 注册配置界面入口。
     *
     * ★ 关键：整个方法只引用 Forge / Minecraft 的类，不 import 任何 Cloth Config 的类。
     *   Cloth Config 的实际代码在 HandMadeClothConfigIntegration 里，
     *   通过 Class.forName 延迟加载，确保未安装 Cloth Config 时不会 NoClassDefFoundError。
     */
    private static void registerConfigScreen() {
        // 未安装 Cloth Config 则静默跳过
        if (!ModList.get().isLoaded("cloth_config")) {
            return;
        }
        try {
            Class<?> integration = Class.forName(
                    "com.alben.createhandmade.client.HandMadeClothConfigIntegration");
            java.lang.reflect.Method create = integration.getMethod("create", Screen.class);

            ConfigScreenHandler.ConfigScreenFactory factory =
                    new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> {
                        try {
                            return (Screen) create.invoke(null, parent);
                        } catch (Throwable t) {
                            CreateHandMade.LOGGER.error(
                                    "Failed to build Hand Made config screen", t);
                            return null;
                        }
                    });

            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> factory);

            CreateHandMade.LOGGER.info("[HandMade] Cloth Config screen registered.");
        } catch (Throwable t) {
            CreateHandMade.LOGGER.error(
                    "Failed to register Hand Made Cloth Config screen", t);
        }
    }
}
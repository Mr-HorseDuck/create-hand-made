package com.alben.createhandmade;

import com.alben.createhandmade.registry.ModCreativeModeTabs;
import com.alben.createhandmade.registry.ModEnchantments;
import com.alben.createhandmade.registry.ModItems;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.particle.ModParticleTypes;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateHandMade.MODID)
public class CreateHandMade {
    public static final String MODID = "create_hand_made";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateHandMade() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModCreativeModeTabs.register(modEventBus);

        REGISTRATE.registerEventListeners(modEventBus);
        ModItems.register();

        ModParticleTypes.register(modEventBus);

        ModNetwork.register();

        // ★ 附魔注册
        ModEnchantments.register(modEventBus);

        // ★ 配方类型注册（tool_recipe / stonecutting_recipe / bellows_recipe）
        HandMadeRecipeTypes.register(modEventBus);

        // 注册到 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 使用 ModLoadingContext 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        // ★ 提前初始化 TLM 兼容
        //   - MaidNetwork：通道注册必须在 Forge 通道锁关闭前完成
        //   - MaidConfig ：配置注册必须在 mod 构造阶段完成，否则 ConfigValue 没有关联
        //                  的 ModConfig，Cloth Config 保存时会抛
        //                  "Cannot set config value without assigned Config object present"
        //   注：lang_2 / MaidLangOverride 已废弃，改为 lang 二合一
        if (ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class<?> maidNetworkClass = Class.forName(
                        "com.alben.createhandmade.compat.tlm.MaidNetwork");
                java.lang.reflect.Method initMethod = maidNetworkClass.getMethod("init");
                initMethod.invoke(null);

                // ★ 女仆配置 SPEC 注册
                Class<?> maidConfigClass = Class.forName(
                        "com.alben.createhandmade.compat.tlm.MaidConfig");
                java.lang.reflect.Method registerSpecMethod =
                        maidConfigClass.getMethod("registerSpec");
                registerSpecMethod.invoke(null);

            } catch (ClassNotFoundException e) {
                // tlmCompat 未编译，正常跳过
            } catch (Throwable t) {
                LOGGER.error("[HandMade] Failed to init maid compat", t);
            }
        }
    }

    /**
     * ★ 调试日志：仅当 Config.debugEnabled = true 时输出。
     *   用于排查女仆兼容 / 指杆标记 / 配方查询问题。
     *   调用处无需重复写 "[HandMade-Debug]" 前缀，本方法自动加。
     */
    public static void debug(String format, Object... args) {
        if (Config.INSTANCE.debugEnabled.get()) {
            LOGGER.info("[HandMade-Debug] " + format, args);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
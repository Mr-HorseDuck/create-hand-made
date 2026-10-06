package com.alben.createhandmade;

import com.alben.createhandmade.item.ModCreativeModeTabs;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.particle.ModParticleTypes;
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

        // 注册到 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 使用 ModLoadingContext 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // ★ 提前初始化 TLM 兼容的网络通道（必须在 Forge 通道锁关闭前）
        if (ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class<?> maidNetworkClass = Class.forName(
                        "com.alben.createhandmade.compat.tlm.MaidNetwork");
                java.lang.reflect.Method initMethod = maidNetworkClass.getMethod("init");
                initMethod.invoke(null);
            } catch (ClassNotFoundException e) {
                // tlmCompat 未编译，正常跳过
            } catch (Throwable t) {
                LOGGER.error("[HandMade] Failed to init maid network", t);
            }
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
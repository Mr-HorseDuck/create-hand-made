package com.alben.createhandmade;

import com.alben.createhandmade.item.ModCreativeModeTabs;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.particle.ModParticleTypes;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(CreateHandMade.MODID)
public class CreateHandMade {
    public static final String MODID = "create_hand_made";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateHandMade(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModCreativeModeTabs.register(modEventBus);
        ModDataComponents.register(modEventBus);

        REGISTRATE.registerEventListeners(modEventBus);
        ModItems.register();

        ModParticleTypes.register(modEventBus);

        // L3 独占层：自定义 RecipeType / RecipeSerializer（见 HandMadeRecipeTypes）
        HandMadeRecipeTypes.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        // 服务端权威配置：所有工具的时间参数与开关（连接时由服务端同步给客户端，见 ServerConfig 类注释）
        // 注：原先的 COMMON 配置（Config）已并入本文件，Config.java 已删除。
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
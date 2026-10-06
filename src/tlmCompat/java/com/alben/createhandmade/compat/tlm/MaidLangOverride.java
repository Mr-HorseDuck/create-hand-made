package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.CreateHandMade;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "create_hand_made", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MaidLangOverride {

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) MaidLangOverride::onReload);
    }

    private static void onReload(ResourceManager rm) {
        if (!ModList.get().isLoaded("maidassemblyline")) return;

        // ★ 1.20.1 的 getSelected() 返回 String
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        applyOverride(rm, code);
    }

    private static void applyOverride(ResourceManager rm, String langCode) {
        ResourceLocation loc = new ResourceLocation(
                "create_hand_made", "lang_2/" + langCode + ".json");

        Optional<Resource> opt = rm.getResource(loc);
        if (opt.isEmpty()) {
            CreateHandMade.LOGGER.info(
                    "[HandMade] No maid lang override for {}", langCode);
            return;
        }

        try (var is = opt.get().open()) {
            JsonObject json = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);

            Map<String, String> overrides = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                overrides.put(e.getKey(), e.getValue().getAsString());
            }

            // ★ 用 net.minecraft.locale.Language
            Language.getInstance().getLanguageData().putAll(overrides);

            CreateHandMade.LOGGER.info(
                    "[HandMade] Applied {} maid lang overrides for {}",
                    overrides.size(), langCode);

        } catch (Exception e) {
            CreateHandMade.LOGGER.error("[HandMade] Failed to apply maid lang override", e);
        }
    }
}
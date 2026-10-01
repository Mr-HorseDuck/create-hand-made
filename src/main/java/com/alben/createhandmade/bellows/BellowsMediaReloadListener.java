package com.alben.createhandmade.bellows;

import com.alben.createhandmade.CreateHandMade;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BellowsMediaReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "bellows_media";

    public BellowsMediaReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries,
                         ResourceManager manager, ProfilerFiller profiler) {
        List<BellowsMediaEntry> parsed = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> e : entries.entrySet()) {
            try {
                BellowsMediaEntry entry = BellowsMediaEntry.CODEC
                        .parse(JsonOps.INSTANCE, e.getValue())
                        .getOrThrow(false, s -> {});
                parsed.add(entry);
            } catch (Exception ex) {
                CreateHandMade.LOGGER.error("Failed to parse bellows media: {}", e.getKey(), ex);
            }
        }

        BellowsMediaRegistry.reloadData(parsed);

        // ★ 客户端也填充 AUTO_MAP，保证客户端 resolve() 可用
        BellowsMediaRegistry.autoDiscover();
    }
}
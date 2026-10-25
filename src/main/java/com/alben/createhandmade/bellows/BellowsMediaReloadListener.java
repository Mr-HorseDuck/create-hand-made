package com.alben.createhandmade.bellows;

import com.alben.createhandmade.ModRegistration;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** 读取 {@code data/<ns>/bellows_media/*.json}。 */
public class BellowsMediaReloadListener extends SimpleJsonResourceReloadListener<BellowsMediaEntry>
        implements IdentifiableResourceReloadListener {

    public static final String DIRECTORY = "bellows_media";

    private static final Identifier ID = ModRegistration.id("bellows_media");

    public BellowsMediaReloadListener() {
        super(BellowsMediaEntry.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, BellowsMediaEntry> entries,
                         ResourceManager manager, ProfilerFiller profiler) {
        BellowsMediaRegistry.reloadData(entries.values());
        BellowsMediaRegistry.autoDiscover();
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}

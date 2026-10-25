package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.ModRegistration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.BufferedReader;
import java.util.Locale;
import java.util.Map;

/**
 * 配方过滤层 L2 的数据包加载器。
 *
 * <p>读取 {@code data/<namespace>/create_hand_made/tool_filter/<tool_id>.json}。</p>
 */
public class HandMadeRecipeFilterLoader implements ResourceManagerReloadListener, IdentifiableResourceReloadListener {

    public static final String DIRECTORY = "create_hand_made/tool_filter";

    private static final String FIELD_DISABLED = "disabled";
    private static final String FIELD_DISABLED_BY_MOD = "disabled_by_mod";

    private final Identifier fabricId = ModRegistration.id("tool_filter_loader");

    @Override
    public Identifier getFabricId() {
        return fabricId;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        HandMadeRecipeFilters.clearDataPack();

        Map<Identifier, Resource> resources = FileToIdConverter.json(DIRECTORY).listMatchingResources(manager);
        int applied = 0;

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            String toolId = entry.getKey().getPath();

            HandMadeTool tool = parseTool(toolId, entry.getKey());
            if (tool == null) continue;

            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root == null || !root.isJsonObject()) {
                    CreateHandMade.LOGGER.warn("Tool filter {} must be a JSON object — skipping", entry.getKey());
                    continue;
                }
                JsonObject object = root.getAsJsonObject();
                readDisabled(object, tool);
                readDisabledByMod(object, tool);
                applied++;
            } catch (Exception ex) {
                CreateHandMade.LOGGER.warn("Failed to parse tool filter {} — skipping", entry.getKey(), ex);
            }
        }

        if (!resources.isEmpty()) {
            CreateHandMade.LOGGER.info("Loaded {} tool filter file(s) for create_hand_made", applied);
        }
    }

    private static HandMadeTool parseTool(String toolId, Identifier fileId) {
        try {
            return HandMadeTool.valueOf(toolId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            CreateHandMade.LOGGER.warn("Unknown tool id '{}' in tool filter {} — skipping. Valid ids: {}",
                    toolId, fileId, validToolIds());
            return null;
        }
    }

    private static void readDisabled(JsonObject root, HandMadeTool tool) {
        JsonArray ids = arrayField(root, FIELD_DISABLED);
        if (ids == null) return;

        for (JsonElement element : ids) {
            String raw = element.getAsString();
            Identifier recipeId = Identifier.tryParse(raw);
            if (recipeId == null) {
                CreateHandMade.LOGGER.warn("Invalid recipe id '{}' in '{}' for tool {} — skipping entry",
                        raw, FIELD_DISABLED, tool);
                continue;
            }
            HandMadeRecipeFilters.disableFromDataPack(tool, recipeId);
        }
    }

    private static void readDisabledByMod(JsonObject root, HandMadeTool tool) {
        JsonArray mods = arrayField(root, FIELD_DISABLED_BY_MOD);
        if (mods == null) return;

        for (JsonElement element : mods) {
            HandMadeRecipeFilters.disableByModFromDataPack(tool, element.getAsString());
        }
    }

    private static JsonArray arrayField(JsonObject root, String field) {
        if (!root.has(field)) {
            return null;
        }
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonArray()) {
            CreateHandMade.LOGGER.warn("Field '{}' must be a string array — ignoring", field);
            return null;
        }
        return element.getAsJsonArray();
    }

    public static String validToolIds() {
        StringBuilder sb = new StringBuilder();
        for (HandMadeTool tool : HandMadeTool.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(tool.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}

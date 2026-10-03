package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 手搓配方数据包加载器
 *
 * <pre>
 *   L2 过滤:  data/&lt;ns&gt;/handmade/filter/&lt;tool&gt;.json
 *   L3 独占:  data/&lt;ns&gt;/handmade/recipe/&lt;tool&gt;/&lt;name&gt;.json
 * </pre>
 */
public class HandMadeRecipeLoader extends SimplePreparableReloadListener<Void> {

    private static final Gson GSON = new Gson();

    private static final String FILTER_DIR = "handmade/filter";
    private static final String RECIPE_DIR = "handmade/recipe";

    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
        HandMadeRecipePool.reset();
        loadFilters(manager);
        loadCustomRecipes(manager);
    }

    // ================= L2 =================

    private void loadFilters(ResourceManager manager) {
        Map<ResourceLocation, Resource> files = manager.listResources(
                FILTER_DIR, loc -> loc.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String toolName = path.substring(FILTER_DIR.length() + 1,
                    path.length() - ".json".length());
            ToolType tool = ToolType.byId(toolName);
            if (tool == null) {
                CreateHandMade.LOGGER.warn("Unknown tool in filter: {}", fileId);
                continue;
            }

            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null || !json.has("remove")) continue;

                List<ResourceLocation> ids = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray("remove")) {
                    ids.add(new ResourceLocation(e.getAsString()));
                }
                HandMadeRecipePool.addFilter(tool, ids);
            } catch (Exception ex) {
                CreateHandMade.LOGGER.error("Failed to load filter {}", fileId, ex);
            }
        }
    }

    // ================= L3 =================

    private void loadCustomRecipes(ResourceManager manager) {
        Map<ResourceLocation, Resource> files = manager.listResources(
                RECIPE_DIR, loc -> loc.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String relative = path.substring(RECIPE_DIR.length() + 1);
            int slash = relative.indexOf('/');
            if (slash < 0) continue;
            String toolName = relative.substring(0, slash);
            ToolType tool = ToolType.byId(toolName);
            if (tool == null) {
                CreateHandMade.LOGGER.warn("Unknown tool in custom recipe: {}", fileId);
                continue;
            }

            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;

                String recipePath = path.substring(0, path.length() - ".json".length());
                ResourceLocation recipeId = new ResourceLocation(fileId.getNamespace(), recipePath);

                HandMadeRecipePool.addCustom(tool, recipeId, json);
            } catch (Exception ex) {
                CreateHandMade.LOGGER.error("Failed to load custom recipe {}", fileId, ex);
            }
        }
    }
}
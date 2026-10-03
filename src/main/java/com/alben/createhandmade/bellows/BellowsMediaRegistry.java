package com.alben.createhandmade.bellows;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BellowsMediaRegistry {

    private static volatile List<Pair<Ingredient, FanProcessingType>> DATA_ENTRIES = List.of();
    private static volatile Map<Item, FanProcessingType> AUTO_MAP = Map.of();

    private static final Map<FanProcessingType, Pair<TagKey<Block>, TagKey<Fluid>>> TYPE_TAGS;

    static {
        Map<FanProcessingType, Pair<TagKey<Block>, TagKey<Fluid>>> m = new HashMap<>();
        m.put(AllFanProcessingTypes.BLASTING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_BLASTING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_BLASTING.tag));
        m.put(AllFanProcessingTypes.HAUNTING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_HAUNTING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_HAUNTING.tag));
        m.put(AllFanProcessingTypes.SMOKING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SMOKING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_SMOKING.tag));
        m.put(AllFanProcessingTypes.SPLASHING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SPLASHING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_SPLASHING.tag));
        TYPE_TAGS = Map.copyOf(m);
    }

    public static void reloadData(Collection<BellowsMediaEntry> files) {
        List<Pair<Ingredient, FanProcessingType>> flat = new ArrayList<>();
        for (BellowsMediaEntry file : files) {
            FanProcessingType type = FanProcessingType.parse(file.processingTypeId().toString());
            if (type == null) continue;
            for (Ingredient ing : file.ingredients()) {
                flat.add(Pair.of(ing, type));
            }
        }
        DATA_ENTRIES = ImmutableList.copyOf(flat);
    }

    public static void autoDiscover() {
        Map<Item, FanProcessingType> map = new HashMap<>();
        for (var e : TYPE_TAGS.entrySet()) {
            FanProcessingType type = e.getKey();
            TagKey<Block> blockTag = e.getValue().getLeft();
            TagKey<Fluid> fluidTag = e.getValue().getRight();

            for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
                // ★ 排除烈焰人燃烧室从 SMOKING 列表（避免和 BLASTING 重合）
                if (type == AllFanProcessingTypes.SMOKING
                        && h.value() == AllBlocks.BLAZE_BURNER.get()) {
                    continue;
                }

                Item item = h.value().asItem();
                if (item != Items.AIR) map.putIfAbsent(item, type);
            }

            for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Item bucket = h.value().getBucket();
                if (bucket != Items.AIR) map.putIfAbsent(bucket, type);
            }
        }
        AUTO_MAP = Map.copyOf(map);
    }

    /**
     * ★ 惰性填充：如果 AUTO_MAP 为空（客户端标签还没同步），立即重算一次。
     * 客户端第一次调用 resolve / getMediaFor 时，标签同步通常已完成。
     */
    private static void ensureAutoDiscovered() {
        if (AUTO_MAP.isEmpty()) {
            autoDiscover();
        }
    }

    @Nullable
    public static FanProcessingType resolve(ItemStack stack) {
        if (stack.isEmpty()) return null;

        for (Pair<Ingredient, FanProcessingType> entry : DATA_ENTRIES) {
            if (entry.getLeft().test(stack)) {
                return entry.getRight();
            }
        }

        // ★ 惰性触发
        ensureAutoDiscovered();
        return AUTO_MAP.get(stack.getItem());
    }

    /**
     * 返回指定类型的所有介质物品。
     */
    public static List<ItemStack> getMediaFor(FanProcessingType type) {
        // ★ 惰性触发
        ensureAutoDiscovered();

        List<ItemStack> result = new ArrayList<>();
        Set<Item> seen = new HashSet<>();

        for (Pair<Ingredient, FanProcessingType> entry : DATA_ENTRIES) {
            if (entry.getRight() != type) continue;
            for (ItemStack stack : entry.getLeft().getItems()) {
                if (stack.isEmpty()) continue;
                if (seen.add(stack.getItem())) {
                    result.add(stack.copy());
                }
            }
        }

        for (Map.Entry<Item, FanProcessingType> e : AUTO_MAP.entrySet()) {
            if (e.getValue() != type) continue;
            if (seen.add(e.getKey())) {
                result.add(new ItemStack(e.getKey()));
            }
        }

        return result;
    }
}
package com.alben.createhandmade.bellows;

import com.zurrtum.create.AllBlockTags;
import com.zurrtum.create.AllFluidTags;
import com.zurrtum.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.zurrtum.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.function.Consumer;

/**
 * 风箱介质表：数据包自定义条目 + 由 Create 催化剂标签自动发现的条目。
 *
 * <p>数据包条目存的是原始「选择器」字符串（物品 id 或 {@code #标签}），
 * 到真正匹配/枚举时才查注册表与标签 —— 见 {@link BellowsMediaEntry} 的说明。</p>
 */
public final class BellowsMediaRegistry {

    /** 一条数据包介质：一组选择器 + 对应的鼓风类型。 */
    private record MediaEntry(List<String> selectors, FanProcessingType type) {
    }

    private static volatile List<MediaEntry> DATA_ENTRIES = List.of();
    private static volatile Map<Item, FanProcessingType> AUTO_MAP = Map.of();

    private static final Map<FanProcessingType, Pair<TagKey<Block>, TagKey<Fluid>>> TYPE_TAGS;

    static {
        Map<FanProcessingType, Pair<TagKey<Block>, TagKey<Fluid>>> m = new HashMap<>();
        m.put(AllFanProcessingTypes.BLASTING,
                Pair.of(AllBlockTags.FAN_PROCESSING_CATALYSTS_BLASTING,
                        AllFluidTags.FAN_PROCESSING_CATALYSTS_BLASTING));
        m.put(AllFanProcessingTypes.HAUNTING,
                Pair.of(AllBlockTags.FAN_PROCESSING_CATALYSTS_HAUNTING,
                        AllFluidTags.FAN_PROCESSING_CATALYSTS_HAUNTING));
        m.put(AllFanProcessingTypes.SMOKING,
                Pair.of(AllBlockTags.FAN_PROCESSING_CATALYSTS_SMOKING,
                        AllFluidTags.FAN_PROCESSING_CATALYSTS_SMOKING));
        m.put(AllFanProcessingTypes.SPLASHING,
                Pair.of(AllBlockTags.FAN_PROCESSING_CATALYSTS_SPLASHING,
                        AllFluidTags.FAN_PROCESSING_CATALYSTS_SPLASHING));
        TYPE_TAGS = Map.copyOf(m);
    }

    private BellowsMediaRegistry() {
    }

    public static void reloadData(Collection<BellowsMediaEntry> files) {
        List<MediaEntry> flat = new ArrayList<>();
        for (BellowsMediaEntry file : files) {
            FanProcessingType type = FanProcessingType.parse(file.processingTypeId().toString());
            if (type == null) {
                continue;
            }
            if (file.ingredients().isEmpty()) {
                continue;
            }
            flat.add(new MediaEntry(List.copyOf(file.ingredients()), type));
        }
        DATA_ENTRIES = List.copyOf(flat);
    }

    public static void autoDiscover() {
        Map<Item, FanProcessingType> map = new HashMap<>();
        for (var e : TYPE_TAGS.entrySet()) {
            FanProcessingType type = e.getKey();
            TagKey<Block> blockTag = e.getValue().getLeft();
            TagKey<Fluid> fluidTag = e.getValue().getRight();

            for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
                Item item = h.value().asItem();
                if (item != Items.AIR) {
                    map.putIfAbsent(item, type);
                }
            }
            for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Item bucket = h.value().getBucket();
                if (bucket != Items.AIR) {
                    map.putIfAbsent(bucket, type);
                }
            }
        }
        AUTO_MAP = Map.copyOf(map);
    }

    /**
     * 惰性填充：如果 AUTO_MAP 为空（客户端标签还没同步），立即重算一次。
     */
    private static void ensureAutoDiscovered() {
        if (AUTO_MAP.isEmpty()) {
            autoDiscover();
        }
    }

    @Nullable
    public static FanProcessingType resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        for (MediaEntry entry : DATA_ENTRIES) {
            for (String selector : entry.selectors()) {
                if (matchesSelector(selector, stack)) {
                    return entry.type();
                }
            }
        }

        ensureAutoDiscovered();
        return AUTO_MAP.get(stack.getItem());
    }

    /** 返回指定类型的所有介质物品。 */
    public static List<ItemStack> getMediaFor(FanProcessingType type) {
        ensureAutoDiscovered();

        List<ItemStack> result = new ArrayList<>();
        Set<Item> seen = new HashSet<>();

        for (MediaEntry entry : DATA_ENTRIES) {
            if (entry.type() != type) {
                continue;
            }
            for (String selector : entry.selectors()) {
                collectSelector(selector, item -> {
                    if (item != Items.AIR && seen.add(item)) {
                        result.add(new ItemStack(item));
                    }
                });
            }
        }

        for (Map.Entry<Item, FanProcessingType> e : AUTO_MAP.entrySet()) {
            if (e.getValue() != type) {
                continue;
            }
            if (seen.add(e.getKey())) {
                result.add(new ItemStack(e.getKey()));
            }
        }

        return result;
    }

    // ==================== 选择器解析 ====================

    /** {@code "#ns:tag"} → 物品标签；{@code "ns:id"} → 单个物品。 */
    private static boolean matchesSelector(String selector, ItemStack stack) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        if (selector.charAt(0) == '#') {
            TagKey<Item> tag = itemTag(selector.substring(1));
            return tag != null && stack.getItem().builtInRegistryHolder().is(tag);
        }
        Identifier id = Identifier.tryParse(selector);
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> item == stack.getItem())
                .orElse(false);
    }

    /** 把选择器展开成具体物品，交给 {@code sink}。 */
    private static void collectSelector(String selector, Consumer<Item> sink) {
        if (selector == null || selector.isEmpty()) {
            return;
        }
        if (selector.charAt(0) == '#') {
            TagKey<Item> tag = itemTag(selector.substring(1));
            if (tag == null) {
                return;
            }
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                sink.accept(h.value());
            }
            return;
        }
        Identifier id = Identifier.tryParse(selector);
        if (id == null) {
            return;
        }
        BuiltInRegistries.ITEM.getOptional(id).ifPresent(sink);
    }

    @Nullable
    private static TagKey<Item> itemTag(String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }
}

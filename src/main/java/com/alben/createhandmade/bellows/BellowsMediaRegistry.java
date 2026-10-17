package com.alben.createhandmade.bellows;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风箱的「副手介质」注册表：把<b>物品</b>映射到<b>鼓风类型的 id</b>
 * （{@link ResourceLocation}，例如 {@code create:blasting}）。
 *
 * <h3>为什么存 id 而不是类型对象（T11 批次 1）</h3>
 * <p>以前这里存的是本模组自己的 {@code FanType} 枚举（只有 4 个值），于是"哪些介质算哪种鼓风"
 * 被永久锁死在 Create 的 4 个内置类型上。现在统一用 id：类型对象由调用方通过
 * {@code CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id)} 动态取，于是任何<b>注册进
 * Create 注册表</b>的第三方鼓风类型都能被本模组表达。</p>
 *
 * <h3>已知盲区（本批次刻意不处理）</h3>
 * <ul>
 *   <li>第三方<b>自定义标签</b>（{@code <mod>:fan_processing_catalysts/<type>}）不会被自动发现；
 *       那需要用户手写数据包条目。</li>
 *   <li>同一物品命中多个类型时的<b>去重 / 优先级</b>未处理。</li>
 * </ul>
 */
public class BellowsMediaRegistry {

    /** 数据包条目：{@code (ingredients, 类型 id)}。 */
    private static volatile List<Pair<Ingredient, ResourceLocation>> DATA_ENTRIES = List.of();

    /** 自动发现结果：物品 → 类型 id。 */
    private static volatile Map<Item, ResourceLocation> AUTO_MAP = Map.of();

    /** Create 的 4 组标准触媒标签，键是<b>类型 id</b>（= {@code AllFanProcessingTypes} 的注册 id）。 */
    private static final Map<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> TYPE_TAGS;

    private static final ResourceLocation BLASTING = ResourceLocation.fromNamespaceAndPath("create", "blasting");
    private static final ResourceLocation SMOKING = ResourceLocation.fromNamespaceAndPath("create", "smoking");
    private static final ResourceLocation HAUNTING = ResourceLocation.fromNamespaceAndPath("create", "haunting");
    private static final ResourceLocation SPLASHING = ResourceLocation.fromNamespaceAndPath("create", "splashing");

    static {
        Map<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> m = new HashMap<>();
        m.put(BLASTING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_BLASTING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_BLASTING.tag));
        m.put(SMOKING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SMOKING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_SMOKING.tag));
        m.put(HAUNTING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_HAUNTING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_HAUNTING.tag));
        m.put(SPLASHING,
                Pair.of(AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SPLASHING.tag,
                        AllTags.AllFluidTags.FAN_PROCESSING_CATALYSTS_SPLASHING.tag));
        TYPE_TAGS = Map.copyOf(m);
    }

    /** 数据包重载：把 {@link BellowsMediaEntry} 摊平成 (ingredients, 类型 id) 列表。 */
    public static void reloadData(Collection<BellowsMediaEntry> files) {
        List<Pair<Ingredient, ResourceLocation>> flat = new ArrayList<>();
        for (BellowsMediaEntry file : files) {
            ResourceLocation typeId = file.processingTypeId();
            for (Ingredient ing : file.ingredients()) {
                flat.add(Pair.of(ing, typeId));
            }
        }
        DATA_ENTRIES = ImmutableList.copyOf(flat);
    }

    /** 扫 Create 的 4 组标准标签，填充 {@link #AUTO_MAP}（方块 → asItem，流体 → 桶）。 */
    public static void autoDiscover() {
        Map<Item, ResourceLocation> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> e : TYPE_TAGS.entrySet()) {
            ResourceLocation typeId = e.getKey();
            TagKey<Block> blockTag = e.getValue().getLeft();
            TagKey<Fluid> fluidTag = e.getValue().getRight();

            for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
                Item item = h.value().asItem();
                if (item != Items.AIR) map.putIfAbsent(item, typeId);
            }
            for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Item bucket = h.value().getBucket();
                if (bucket != Items.AIR) map.putIfAbsent(bucket, typeId);
            }
        }
        AUTO_MAP = Map.copyOf(map);
    }

    /**
     * ★ 惰性填充：如果 {@link #AUTO_MAP} 为空（客户端标签还没同步），立即重算一次。
     * 客户端第一次调用 resolve / getMediaFor 时，标签同步通常已完成。
     */
    private static void ensureAutoDiscovered() {
        if (AUTO_MAP.isEmpty()) {
            autoDiscover();
        }
    }

    /**
     * 解析副手介质对应的鼓风类型 id。
     *
     * @return 类型 id（{@code create:blasting} 等）；不是介质时返回 {@code null}。
     *         调用方再用 {@code CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id)} 取类型对象。
     */
    @Nullable
    public static ResourceLocation resolve(ItemStack stack) {
        if (stack.isEmpty()) return null;

        for (Pair<Ingredient, ResourceLocation> entry : DATA_ENTRIES) {
            if (entry.getLeft().test(stack)) {
                return entry.getRight();
            }
        }

        // ★ 惰性触发
        ensureAutoDiscovered();
        return AUTO_MAP.get(stack.getItem());
    }

    /** 返回指定类型 id 的所有介质物品。 */
    public static List<ItemStack> getMediaFor(ResourceLocation typeId) {
        // ★ 惰性触发
        ensureAutoDiscovered();

        List<ItemStack> result = new ArrayList<>();
        Set<Item> seen = new HashSet<>();

        for (Pair<Ingredient, ResourceLocation> entry : DATA_ENTRIES) {
            if (!entry.getRight().equals(typeId)) continue;
            for (ItemStack stack : entry.getLeft().getItems()) {
                if (stack.isEmpty()) continue;
                if (seen.add(stack.getItem())) {
                    result.add(stack.copy());
                }
            }
        }

        for (Map.Entry<Item, ResourceLocation> e : AUTO_MAP.entrySet()) {
            if (!e.getValue().equals(typeId)) continue;
            if (seen.add(e.getKey())) {
                result.add(new ItemStack(e.getKey()));
            }
        }

        return result;
    }
}

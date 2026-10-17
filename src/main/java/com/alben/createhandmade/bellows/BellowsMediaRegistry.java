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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <h3>T11 批次 2：一个介质可以对应多个类型（去重在这里，选择在使用方）</h3>
 * <p>装了 DnD / Create: Processing 等多个鼓风扩展后，同一介质（例如沙子）可能同时关联到
 * {@code dndesires:sanding}、{@code create_processing:sanding} 等多个类型。
 * 本类只负责<b>把所有候选 id 收集全</b>（去重 + 保序），
 * "到底触发哪一个"由 {@code BellowsItem} 按「能不能加工这一件」+「{@code getPriority()}」决定 ——
 * 这样触媒判定和优先级策略各归各处。</p>
 *
 * <h3>两个来源（数据包优先，自动发现补充）</h3>
 * <ol>
 *   <li><b>数据包</b>（{@code data/<ns>/bellows_media/*.json}，见 {@link BellowsMediaEntry}）：
 *       {@code type} 是类型 id（<b>必须是带命名空间的完整 id</b>），{@code ingredients} 是介质物品。
 *       同一物品写多条即"多候选"。</li>
 *   <li><b>自动发现</b>（{@link #autoDiscover()}）：扫 Create 的 4 组标准标签
 *       （{@code create:fan_processing_catalysts/<type>} 的 block / fluid 两套），
 *       把命中的方块 {@code asItem()}、流体 {@code getBucket()} 收集到对应类型 id。</li>
 * </ol>
 *
 * <h3>已知盲区（本批次不处理）</h3>
 * <p>第三方<b>自定义标签</b>（{@code <mod>:fan_processing_catalysts/<type>}）不会被自动发现；
 * 那需要用户手写数据包条目。</p>
 */
public class BellowsMediaRegistry {

    /** 数据包条目：{@code (ingredients, 类型 id)}。 */
    private static volatile List<Pair<Ingredient, ResourceLocation>> DATA_ENTRIES = List.of();

    /** 自动发现结果：物品 → 它能触发的<b>全部</b>类型 id（同一物品可能同时属于多个 Create 标签）。 */
    private static volatile Map<Item, List<ResourceLocation>> AUTO_MAP = Map.of();

    /** Create 的 4 组标准触媒标签，键是<b>类型 id</b>（= {@code AllFanProcessingTypes} 的注册 id）。 */
    private static final Map<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> TYPE_TAGS;

    private static final ResourceLocation BLASTING = ResourceLocation.fromNamespaceAndPath("create", "blasting");
    private static final ResourceLocation SMOKING = ResourceLocation.fromNamespaceAndPath("create", "smoking");
    private static final ResourceLocation HAUNTING = ResourceLocation.fromNamespaceAndPath("create", "haunting");
    private static final ResourceLocation SPLASHING = ResourceLocation.fromNamespaceAndPath("create", "splashing");

    static {
        // ★ LinkedHashMap：保证自动发现时"同一物品命中多个标签"的收集顺序<b>固定</b>
        //   （blasting → smoking → haunting → splashing）。以前用 HashMap + Map.copyOf，
        //   顺序不确定 ⇒ 多类型时最终选中哪个是随机的。
        Map<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> m = new LinkedHashMap<>();
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

    /**
     * 扫 Create 的 4 组标准标签，填充 {@link #AUTO_MAP}（方块 → asItem，流体 → 桶）。
     *
     * <p>同一物品若同时出现在多个标签里（某模组把方块既算 blasting 又算 smoking），
     * 这里会把它收集成<b>多个</b> id —— 最终选哪个由使用方按优先级决定，
     * 不再像以前那样"先到先得"。</p>
     */
    public static void autoDiscover() {
        Map<Item, List<ResourceLocation>> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Pair<TagKey<Block>, TagKey<Fluid>>> e : TYPE_TAGS.entrySet()) {
            ResourceLocation typeId = e.getKey();
            TagKey<Block> blockTag = e.getValue().getLeft();
            TagKey<Fluid> fluidTag = e.getValue().getRight();

            for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
                Item item = h.value().asItem();
                if (item != Items.AIR) addTypeId(map, item, typeId);
            }
            for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Item bucket = h.value().getBucket();
                if (bucket != Items.AIR) addTypeId(map, bucket, typeId);
            }
        }
        Map<Item, List<ResourceLocation>> frozen = new HashMap<>();
        map.forEach((item, ids) -> frozen.put(item, List.copyOf(ids)));
        AUTO_MAP = Map.copyOf(frozen);
    }

    private static void addTypeId(Map<Item, List<ResourceLocation>> map, Item item, ResourceLocation typeId) {
        List<ResourceLocation> ids = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (!ids.contains(typeId)) {
            ids.add(typeId);
        }
    }

    /**
     * ★ 惰性填充：如果 {@link #AUTO_MAP} 为空（客户端标签还没同步），立即重算一次。
     * 客户端第一次调用 resolveAll / getMediaFor 时，标签同步通常已完成。
     */
    private static void ensureAutoDiscovered() {
        if (AUTO_MAP.isEmpty()) {
            autoDiscover();
        }
    }

    /**
     * 解析副手介质能触发的<b>所有</b>鼓风类型 id（去重、保序：数据包条目在前，自动发现补在后）。
     *
     * <p>T11 B2 起不再"首个命中即返回" —— 同一介质可能因为装了多个鼓风扩展模组而同时关联到
     * 多个类型。选哪个由调用方按「能不能加工这一件」+「{@code getPriority()}」决定，
     * 见 {@code BellowsItem.applyToTarget}。</p>
     *
     * @return 类型 id 列表；不是介质时返回空列表（不返回 null，省掉调用方判空）
     */
    public static List<ResourceLocation> resolveAll(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Pair<Ingredient, ResourceLocation> entry : DATA_ENTRIES) {
            if (entry.getLeft().test(stack)) {
                ids.add(entry.getRight());
            }
        }

        // ★ 惰性触发
        ensureAutoDiscovered();
        List<ResourceLocation> auto = AUTO_MAP.get(stack.getItem());
        if (auto != null) {
            ids.addAll(auto);
        }
        return List.copyOf(ids);
    }

    /** 该物品是否<b>至少</b>能触发一个鼓风类型（渲染器/预览用，避免为了判空而构造列表）。 */
    public static boolean isMedia(ItemStack stack) {
        return !resolveAll(stack).isEmpty();
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

        for (Map.Entry<Item, List<ResourceLocation>> e : AUTO_MAP.entrySet()) {
            if (!e.getValue().contains(typeId)) continue;
            if (seen.add(e.getKey())) {
                result.add(new ItemStack(e.getKey()));
            }
        }

        return result;
    }
}

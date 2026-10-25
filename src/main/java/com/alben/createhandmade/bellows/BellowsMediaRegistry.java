package com.alben.createhandmade.bellows;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.ServerConfig;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
 *   <li><b>自动发现</b>（{@link #autoDiscover()}）：扫 <b>所有</b> block / item / fluid 标签中
 *       名字像触媒的（{@code <ns>:fan_processing_catalysts/<type>}、{@code <ns>:fan_catalysts/<type>}），
 *       把命中的方块展开成物品、流体展开成桶，收集到对应类型 id（T11 B3 泛化）。</li>
 * </ol>
 *
 * <h3>优先级：数据包可以覆盖（T11 B4）</h3>
 * <p>不同模组的 {@code getPriority()} 尺度不统一（CMC 动辄 2000+），多模组共存时会互相饿死。
 * {@link BellowsMediaEntry} 的 {@code priority} 字段允许数据包<b>直接指定</b>某类型的优先级，
 * 由 {@link #priorityOverride(ResourceLocation)} 暴露；使用方（{@code BellowsItem}）看到覆盖值就无视
 * {@code getPriority()}。这样"想让 DnD 的冷冻压过 CMC 的雪流"只需要一个数据包文件。</p>
 */
public class BellowsMediaRegistry {

    /**
     * 数据包条目展开后的一个"绑定"：要么是一条 {@code ingredients} 原料，
     * 要么是 {@code catalysts} 标签展开出的某个具体物品（T11 B3）。
     */
    private record Binding(ResourceLocation typeId, @Nullable Ingredient ingredient, @Nullable Item item) {

        private boolean matches(ItemStack stack) {
            if (item != null) return stack.getItem() == item;
            return ingredient != null && ingredient.test(stack);
        }
    }

    /** 数据包条目（已展开）：保留数据包顺序。 */
    private static volatile List<Binding> DATA_ENTRIES = List.of();

    /** 数据包 {@code fluids} 字段声明的流体介质：类型 id → 流体。 */
    private static volatile Map<ResourceLocation, List<Fluid>> DATA_FLUID_MEDIA = Map.of();

    /** 自动发现扫到的"无桶流体"：类型 id → 流体。与 {@link #DATA_FLUID_MEDIA} 分开存，互不覆盖。 */
    private static volatile Map<ResourceLocation, List<Fluid>> AUTO_FLUID_MEDIA = Map.of();

    /**
     * 某个鼓风类型在"流体介质"上的绑定（T11 B8），用于 JEI 展示与诊断。
     *
     * <p>合并两个来源（数据包优先、自动发现补后），去重保序。
     * 注意：<b>流体本身不能当副手介质</b>（玩家只能拿物品），有桶的流体在绑定阶段已经
     * 顺手把桶绑成物品介质了；没有桶的流体只能在这里登记。</p>
     */
    public static List<Fluid> getFluidsFor(ResourceLocation typeId) {
        Set<Fluid> out = new LinkedHashSet<>();
        out.addAll(DATA_FLUID_MEDIA.getOrDefault(typeId, List.of()));
        out.addAll(AUTO_FLUID_MEDIA.getOrDefault(typeId, List.of()));
        return List.copyOf(out);
    }

    /**
     * 数据包显式指定的优先级覆盖（T11 B4）：类型 id → 优先级。
     *
     * <p>键是<b>类型 id</b>（不是物品、也不是数据包文件），所以一个覆盖条目对所有走到该类型的介质都生效。
     * 没配覆盖的类型不在表里，调用方回落到 {@code FanProcessingType#getPriority()}。</p>
     */
    private static volatile Map<ResourceLocation, Integer> PRIORITY_OVERRIDES = Map.of();

    /** 类型级禁用（{@code bellows_type_overrides} 里 {@code <type_id>=none}）。 */
    private static volatile java.util.Set<ResourceLocation> DISABLED_TYPES = java.util.Set.of();

    /** 类型级优先级覆盖（{@code <type_id>=<整数>}）。 */
    private static volatile java.util.Map<ResourceLocation, Integer> TYPE_PRIORITY = java.util.Map.of();

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

    /**
     * 数据包重载：把每条 {@link BellowsMediaEntry} 展开成若干 {@link Binding}。
     *
     * <p>{@code ingredients} 与 {@code catalysts} 可以同时存在，<b>合并生效</b>；
     * 只写其中任意一个也合法。</p>
     */
    public static void reloadData(Collection<BellowsMediaEntry> files) {
        List<Binding> flat = new ArrayList<>();
        Map<ResourceLocation, Integer> priorities = new HashMap<>();
        Map<ResourceLocation, List<Fluid>> dataFluids = new LinkedHashMap<>();
        int withCatalysts = 0;
        int tagItems = 0;
        int fluidOnly = 0;

        for (BellowsMediaEntry file : files) {
            ResourceLocation typeId = file.processingTypeId();

            // ★ T11 B4：优先级覆盖。与介质无关 —— 一个只写 type + priority 的条目也生效。
            file.priority().ifPresent(priority -> {
                Integer previous = priorities.putIfAbsent(typeId, priority);
                if (previous != null && !previous.equals(priority)) {
                    // 两个文件给同一类型配了不同值：取较大者（并警告），保证结果与文件加载顺序无关
                    CreateHandMade.LOGGER.warn(
                            "bellows_media: conflicting priority for {} ({} vs {}); using the larger value",
                            typeId, previous, priority);
                    priorities.put(typeId, Math.max(previous, priority));
                }
            });

            for (Ingredient ing : file.ingredients()) {
                flat.add(new Binding(typeId, ing, null));
            }

            Optional<String> rawTag = file.catalysts();
            if (rawTag.isPresent()) {
                withCatalysts++;
                ResourceLocation tagId = parseTagId(rawTag.get());
                if (tagId != null) {
                    Set<Item> items = itemsInTag(tagId);
                    if (items.isEmpty()) {
                        // 常见原因：该模组没装（标签不存在）或标签名写错 —— 用 debug 避免刷屏
                        CreateHandMade.LOGGER.debug("bellows_media: catalysts tag {} expanded to 0 items", tagId);
                    }
                    for (Item item : items) {
                        flat.add(new Binding(typeId, null, item));
                        tagItems++;
                    }
                }
            }

            // ★ T11 B8：显式声明的流体介质。有桶 ⇒ 顺便把桶绑成物品介质；没桶 ⇒ 只登记（JEI 展示 + 诊断）
            for (ResourceLocation fluidId : file.fluids()) {
                Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
                if (fluid == null || fluid == Fluids.EMPTY) {
                    CreateHandMade.LOGGER.warn("bellows_media: unknown fluid {} for type {} (ignored)",
                            fluidId, typeId);
                    continue;
                }
                addFluid(dataFluids, typeId, fluid);
                Item bucket = fluid.getBucket();
                if (bucket != Items.AIR) {
                    flat.add(new Binding(typeId, null, bucket));
                } else {
                    fluidOnly++;
                }
            }
        }

        DATA_ENTRIES = ImmutableList.copyOf(flat);
        PRIORITY_OVERRIDES = Map.copyOf(priorities);
        DATA_FLUID_MEDIA = freezeFluids(dataFluids);
        CreateHandMade.LOGGER.info(
                "Loaded {} bellows media entries ({} with catalysts, {} catalyst items, {} fluid media "
                        + "({} without bucket item), {} priority overrides)",
                files.size(), withCatalysts, tagItems, dataFluids.values().stream().mapToInt(List::size).sum(),
                fluidOnly, priorities.size());

        // 逐条打印覆盖结果（只有显式配置过的类型才会打，通常 0~几条），方便玩家确认自己的数据包生效了
        priorities.forEach((typeId, value) -> {
            var type = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId);
            if (type == null) {
                CreateHandMade.LOGGER.warn("bellows_media: priority override for unknown type {} ignored", typeId);
            } else {
                CreateHandMade.LOGGER.info("bellows_media: priority override {} = {} (type default {})",
                        typeId, value, type.getPriority());
            }
        });

        // 数据包重载后（例如 /reload 或改了配置再重载）也重算冲突报告；autoDiscover 还没跑过就跳过
        if (!AUTO_MAP.isEmpty()) {
            reportConflicts();
        }
    }

    /**
     * 查某个类型是否被数据包<b>显式</b>指定了优先级（T11 B4）。
     *
     * @return 数据包里的值；没配则返回 {@code null}（调用方回落到 {@code getPriority()}）
     */
    @Nullable
    public static Integer priorityOverride(ResourceLocation typeId) {
        return PRIORITY_OVERRIDES.get(typeId);
    }

    /** 去掉可选的 {@code #} 前缀并解析成 {@link ResourceLocation}；非法时记一条 warn 并返回 null。 */
    @Nullable
    private static ResourceLocation parseTagId(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            CreateHandMade.LOGGER.warn("bellows_media: empty catalysts tag ('{}')", raw);
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            CreateHandMade.LOGGER.warn("bellows_media: invalid catalysts tag '{}' (use '#namespace:path')", raw);
        }
        return id;
    }

    /**
     * 把一个标签 id 展开成物品集合：同时查 <b>block / item / fluid</b> 三个注册表的同名标签。
     *
     * <p>为什么三边都要查：第三方触媒几乎都是 <b>block tag</b>（例如
     * {@code dndesires:fan_processing_catalysts/sanding}），方块要经 {@code asItem()} 才能当介质；
     * {@code #c:ingots} 这类通用标签通常是 <b>item tag</b>；洗涤类触媒可能只挂在 <b>fluid tag</b> 上（取桶）。
     * 三边各扫一次成本很低，能覆盖所有写法。</p>
     */
    private static Set<Item> itemsInTag(ResourceLocation tagId) {
        Set<Item> items = new LinkedHashSet<>();

        for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, tagId))) {
            Item item = itemForBlock(h.value());
            if (item != Items.AIR) items.add(item);
        }
        for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
            if (h.value() != Items.AIR) items.add(h.value());
        }
        for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(TagKey.create(Registries.FLUID, tagId))) {
            Item bucket = h.value().getBucket();
            if (bucket != Items.AIR) items.add(bucket);
        }

        return items;
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
        Map<ResourceLocation, List<Fluid>> fluids = new LinkedHashMap<>();
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
                if (bucket != Items.AIR) {
                    addTypeId(map, bucket, typeId);
                } else {
                    // 无桶流体（例如龙+ 的染料液体）：不能当副手介质，但登记下来供 JEI 展示/诊断
                    addFluid(fluids, typeId, h.value());
                }
            }
        }
        // ★ T11 B3：泛化扫描所有第三方触媒标签（<ns>:fan_processing_catalysts/<type> 等）
        scanCatalystTags(map, fluids);
        // ★ T11 B9：个别模组既没有物品标签也没有桶标签，只能按 id 规律认（龙+ 的染料桶）
        scanItemPatterns(map);

        Map<Item, List<ResourceLocation>> frozen = new HashMap<>();
        map.forEach((item, ids) -> frozen.put(item, List.copyOf(ids)));
        AUTO_MAP = Map.copyOf(frozen);
        AUTO_FLUID_MEDIA = freezeFluids(fluids);

        // 诊断：按类型统计媒体数量 —— 一眼看出"某个第三方类型到底有没有介质"
        // （上一轮排查 DnD/龙+ 失效时就缺这个视角；DEBUG 日志在开发环境默认可见）
        if (CreateHandMade.LOGGER.isDebugEnabled()) {
            Map<String, Integer> perType = new java.util.TreeMap<>();
            map.forEach((item, ids) -> ids.forEach(id -> { if (isSuppressed(item, id)) return; perType.merge(id.toString(), 1, Integer::sum); }));
            CreateHandMade.LOGGER.debug("bellows media by fan type: {}", perType);
            // 再把"已注册但一个介质都没有"的类型单独列出来：这类类型从副手永远触发不了，
            // 要么是上游没给标签（龙+ 的冷冻/磨砂），要么是只支持真实鼓风机。
            List<String> withoutMedia = new ArrayList<>();
            for (ResourceLocation id : CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet()) {
                if (!perType.containsKey(id.toString())) withoutMedia.add(id.toString());
            }
            CreateHandMade.LOGGER.debug("fan types without any bellows media: {}", withoutMedia);
        }

        // ★ T11 B5：两个来源都齐了，出冲突报告
        reportConflicts();
    }

    /**
     * 按"物品 id 去掉后缀 = 鼓风类型 id"的规律识别触媒（T11 B9）。
     *
     * <h3>为什么需要</h3>
     * <p>Create: Dragons Plus 的染色触媒是<b>流体</b>，通过
     * {@code data_maps/fluid/fan_processing_catalysts/coloring.json} 绑定，<b>既没有</b>
     * {@code fan_processing_catalysts/coloring} 物品标签，<b>也没有</b>桶标签 —— 但它的 51 个染料桶
     * {@code create_dragons_plus:<color>_dye_bucket} 是真实存在的物品，而且染色鼓风类型是
     * <b>按染料变体各注册一个</b>。这类"有物品、无标签"的情况只能按 id 规律识别。</p>
     *
     * <p>规则刻意写得很克制：只做"去掉 {@code _dye_bucket} 后缀，看是否正好是一个已注册的
     * 鼓风类型 id"，命不中就什么都不做；因此对其它模组无副作用，将来对方补了标签也可以整块删除。</p>
     */
    private static void scanItemPatterns(Map<Item, List<ResourceLocation>> map) {
        int matched = 0;
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            String path = itemId.getPath();
            // 已知两种写法：龙+ 的 <color>_dye_bucket、创意传动的 <color>_fan_dyeing_catalyst
            String suffix = null;
            for (String s : DYE_LIKE_SUFFIXES) {
                if (path.endsWith(s) && path.length() > s.length()) {
                    suffix = s;
                    break;
                }
            }
            if (suffix == null) continue;

            String variant = path.substring(0, path.length() - suffix.length());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) continue;

            // 候选类型：<ns>:<variant> / <ns>:coloring_<variant> / create_dragons_plus:coloring_<variant>
            // （第三个是为了让创意传动的染色触媒在"非龙+"命名空间下也能绑到龙+ 的染色类型上）
            boolean bound = false;
            for (String prefix : DYE_TYPE_PREFIXES) {
                ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath(
                        itemId.getNamespace(), prefix + variant);
                if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId) == null) continue;
                addTypeId(map, item, typeId);
                bound = true;
                break;
            }
            if (!bound) {
                ResourceLocation dpType = ResourceLocation.fromNamespaceAndPath(
                        "create_dragons_plus", "coloring_" + variant);
                if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(dpType) != null) {
                    addTypeId(map, item, dpType);
                    bound = true;
                }
            }
            if (bound) matched++;
        }
        if (matched > 0) {
            CreateHandMade.LOGGER.info(
                    "Auto-discovered {} dye catalyst item(s) via id-suffix rule ({} → <ns>:[coloring_]<name>)",
                    matched, DYE_LIKE_SUFFIXES);
        }
    }

    private static void addFluid(Map<ResourceLocation, List<Fluid>> map, ResourceLocation typeId, Fluid fluid) {
        List<Fluid> list = map.computeIfAbsent(typeId, k -> new ArrayList<>());
        if (!list.contains(fluid)) list.add(fluid);
    }

    private static Map<ResourceLocation, List<Fluid>> freezeFluids(Map<ResourceLocation, List<Fluid>> map) {
        Map<ResourceLocation, List<Fluid>> frozen = new HashMap<>();
        map.forEach((typeId, fluids) -> frozen.put(typeId, List.copyOf(fluids)));
        return Map.copyOf(frozen);
    }

    private static void addTypeId(Map<Item, List<ResourceLocation>> map, Item item, ResourceLocation typeId) {
        List<ResourceLocation> ids = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (!ids.contains(typeId)) {
            ids.add(typeId);
        }
    }

    // ================= 泛化标签扫描（T11 B3） =================

    /** 龙+ 染料桶的 id 后缀：物品 {@code <ns>:<variant>_dye_bucket} 对应类型 {@code <ns>:<variant>}。 */
    private static final String DYE_BUCKET_SUFFIX = "_dye_bucket";

    /** 染料桶 id 的主干可能直接就是类型名，也可能带 {@code coloring_} 前缀（龙+ 实测是后者）。 */
    private static final List<String> DYE_TYPE_PREFIXES = List.of("", "coloring_");

    /** 按 id 后缀识别染色触媒：龙+ 染料桶、创意传动 {@code <color>_fan_dyeing_catalyst}。 */
    private static final List<String> DYE_LIKE_SUFFIXES = List.of(DYE_BUCKET_SUFFIX, "_fan_dyeing_catalyst");

    /** 常见的第三方触媒标签前缀：{@code <ns>:fan_processing_catalysts/<类型名>} 与 {@code fan_catalysts/<类型名>}。 */
    private static final List<String> CATALYST_TAG_PREFIXES =
            List.of("fan_processing_catalysts/", "fan_catalysts/");

    /**
     * 遍历<b>所有</b> block / fluid 标签，把"名字像触媒标签"且"对应类型确实注册过"的标签
     * 展开成 (物品 → 类型 id)，合进 {@code map}。
     *
     * <p>这一步让本模组能自动识别任意第三方模组的触媒（DnD、Create: Processing、More Catalysts …），
     * 只要对方按"标签名末段 = 类型名"的惯例命名。<b>类型是否存在由 Create 注册表判定</b>
     * （{@code FAN_PROCESSING_TYPE.get(id) != null}），所以没有任何硬编码的类型表。</p>
     */
    private static void scanCatalystTags(Map<Item, List<ResourceLocation>> map,
                                         Map<ResourceLocation, List<Fluid>> fluids) {
        int tagCount = 0;
        int itemCountBefore = map.size();
        int noItemBlocks = 0;
        int rescuedBlocks = 0;
        int bucketlessFluids = 0;
        List<ResourceLocation> skipped = new ArrayList<>();

        // ① 方块标签：触媒的主流写法（Create / DnD / CMC / Processing 全都是 block tag）
        // Registry#getTags() 返回 Stream<Pair<TagKey<T>, HolderSet.Named<T>>>（1.21.1），
        // 直接用它的 second 遍历，省一次 getTagOrEmpty 查询；用 var 避免与 commons Pair 撞名。
        for (var entry : BuiltInRegistries.BLOCK.getTags().toList()) {
            ResourceLocation typeId = catalystTypeIdFor(entry.getFirst().location());
            if (typeId == null) continue;
            if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId) == null) continue;
            tagCount++;

            for (Holder<Block> h : entry.getSecond()) {
                Block block = h.value();
                Item item = block.asItem();
                if (item == Items.AIR) {
                    // asItem() 认不出来时再查一次 BY_BLOCK（见 itemForBlock 的注释）
                    item = Item.BY_BLOCK.getOrDefault(block, Items.AIR);
                    if (item != Items.AIR) {
                        rescuedBlocks++;
                        CreateHandMade.LOGGER.debug(
                                "bellows_media: rescued item-less asItem() for {} -> {} ({} tag)",
                                block, item, typeId);
                    }
                }
                if (item == Items.AIR) {
                    // 没有物品形态 ⇒ 玩家手里拿不出来，当不了副手介质（只能跳过）
                    noItemBlocks++;
                    skipped.add(BuiltInRegistries.BLOCK.getKey(block));
                    continue;
                }
                addTypeId(map, item, typeId);
            }
        }

        // ② 物品标签：少数模组只给物品打标签（不在方块标签里），也一并认
        for (var entry : BuiltInRegistries.ITEM.getTags().toList()) {
            ResourceLocation typeId = catalystTypeIdFor(entry.getFirst().location());
            if (typeId == null) continue;
            if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId) == null) continue;
            tagCount++;

            for (Holder<Item> h : entry.getSecond()) {
                if (h.value() != Items.AIR) addTypeId(map, h.value(), typeId);
            }
        }

        // ③ 流体标签：洗涤类触媒常常只挂在流体标签上，取它的桶
        for (var entry : BuiltInRegistries.FLUID.getTags().toList()) {
            ResourceLocation typeId = catalystTypeIdFor(entry.getFirst().location());
            if (typeId == null) continue;
            if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId) == null) continue;
            tagCount++;

            for (Holder<Fluid> h : entry.getSecond()) {
                Item bucket = h.value().getBucket();
                if (bucket != Items.AIR) {
                    addTypeId(map, bucket, typeId);
                } else {
                    // ★ T11 B8：没有桶物品的流体（龙+ 染料液体等）以前被整个丢掉，
                    //   现在登记进"流体介质"表：不能当副手介质，但 JEI 里能看到、日志里有统计。
                    bucketlessFluids++;
                    addFluid(fluids, typeId, h.value());
                }
            }
        }

        if (tagCount > 0) {
            CreateHandMade.LOGGER.info(
                    "Auto-discovered {} fan catalyst tags ({} new media items, {} total, "
                            + "{} item-less blocks skipped, {} asItem()-rescued, "
                            + "{} bucket-less fluids registered as fluid media)",
                    tagCount, map.size() - itemCountBefore, map.size(), noItemBlocks, rescuedBlocks,
                    bucketlessFluids);
            if (!skipped.isEmpty()) {
                CreateHandMade.LOGGER.debug("bellows_media: catalyst blocks without item form: {}", skipped);
            }
        }
    }

    /**
     * 取方块对应的物品（T11 B4）。
     *
     * <p>{@code Block#asItem()} 内部就是查 {@code Item.BY_BLOCK}（并缓存进 {@code Block.item} 字段），
     * 所以正常情况下两者等价；但那个缓存是<b>首次调用时</b>填的 —— 如果某个模组在
     * {@code BlockItem} 注册之前就调过 {@code asItem()}，缓存里会永久留下 {@code AIR}。
     * 这里再查一次 {@code Item.BY_BLOCK} 兜底，能救回这类"有物品但 asItem() 认不出来"的方块。</p>
     *
     * @return 对应物品；方块确实没有物品形态时返回 {@link Items#AIR}
     */
    private static Item itemForBlock(Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) return item;
        return Item.BY_BLOCK.getOrDefault(block, Items.AIR);
    }

    /**
     * 从一个标签 id 推出它对应的鼓风类型 id；不像触媒标签时返回 null。
     *
     * <p>规则：把路径前缀 {@code fan_processing_catalysts/} / {@code fan_catalysts/} 剥掉，
     * 剩下的部分就是类型名 ⇒ 类型 id = {@code <标签命名空间>:<类型名>}。</p>
     *
     * <p><b>T11 B6 修正：</b>这里原本还有一张 {@code TAG_ID_OVERRIDES} 兜底表，给
     * Create: Dragons Plus 的 {@code fan_sanding_catalysts → bulk_sanding} 做了硬编码映射。
     * 实测那个假设<b>两头都是错的</b>：龙+ 的标签其实就叫
     * {@code create_dragons_plus:fan_processing_catalysts/sanding}（标准写法），
     * 类型 id 也直接是 {@code create_dragons_plus:sanding}（没有 {@code bulk_} 前缀）。
     * 所以通用规则本来就能推出正确结果，那张表已删除。</p>
     */
    @Nullable
    private static ResourceLocation catalystTypeIdFor(ResourceLocation tagId) {
        String path = tagId.getPath();
        for (String prefix : CATALYST_TAG_PREFIXES) {
            if (!path.startsWith(prefix)) continue;
            String typeName = path.substring(prefix.length());
            // 只接受"一段"类型名（fan_processing_catalysts/sanding 而不是 .../sanding/x）
            if (typeName.isEmpty() || typeName.indexOf('/') >= 0) return null;
            return ResourceLocation.fromNamespaceAndPath(tagId.getNamespace(), typeName);
        }
        return null;
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
        for (Binding binding : DATA_ENTRIES) {
            if (binding.matches(stack)) {
                ids.add(binding.typeId());
            }
        }

        // ★ 惰性触发
        ensureAutoDiscovered();
        List<ResourceLocation> auto = AUTO_MAP.get(stack.getItem());
        if (auto != null) {
            ids.addAll(auto);
        }

        // 写死规则（T11 B10）：烈焰人燃烧室只留给烟熏；再补内置声明的媒介
        ids.removeIf(id -> isSuppressed(stack.getItem(), id));
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> e : BUILT_IN_TYPE_MEDIA.entrySet()) {
            for (Item builtIn : builtInMediaFor(e.getKey())) {
                if (builtIn == stack.getItem() && !isSuppressed(builtIn, e.getKey())) {
                    ids.add(e.getKey());
                    break;
                }
            }
        }
        ids.removeIf(BellowsMediaRegistry::isTypeDisabled);
        return List.copyOf(ids);
    }

    /** 该物品是否<b>至少</b>能触发一个鼓风类型（渲染器/预览用，避免为了判空而构造列表）。 */
    /** 左侧是否是"已注册的鼓风类型 id"（用于区分类型级规则与物品级规则）。 */
    private static boolean isFanType(ResourceLocation id) {
        return CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id) != null;
    }

    /** 该鼓风类型是否被配置整体禁用。 */
    public static boolean isTypeDisabled(ResourceLocation typeId) {
        return DISABLED_TYPES.contains(typeId);
    }

    /** 类型级优先级覆盖（没配返回 null）。 */
    @Nullable
    public static Integer typePriorityOverride(ResourceLocation typeId) {
        return TYPE_PRIORITY.get(typeId);
    }

    public static boolean isMedia(ItemStack stack) {
        return !resolveAll(stack).isEmpty();
    }

    /** 返回指定类型 id 的所有介质物品。 */
    public static List<ItemStack> getMediaFor(ResourceLocation typeId) {
        // ★ 惰性触发
        ensureAutoDiscovered();

        List<ItemStack> result = new ArrayList<>();
        Set<Item> seen = new HashSet<>();

        for (Binding binding : DATA_ENTRIES) {
            if (!binding.typeId().equals(typeId)) continue;
            if (binding.item() != null) {
                if (!allowedByRules(binding.item(), typeId)) continue;
                if (seen.add(binding.item())) {
                    result.add(new ItemStack(binding.item()));
                }
            } else if (binding.ingredient() != null) {
                for (ItemStack stack : binding.ingredient().getItems()) {
                    if (stack.isEmpty()) continue;
                    if (!allowedByRules(stack.getItem(), typeId)) continue;
                    if (seen.add(stack.getItem())) {
                        result.add(stack.copy());
                    }
                }
            }
        }

        for (Map.Entry<Item, List<ResourceLocation>> e : AUTO_MAP.entrySet()) {
            if (!e.getValue().contains(typeId)) continue;
            if (!allowedByRules(e.getKey(), typeId)) continue;
            if (seen.add(e.getKey())) {
                result.add(new ItemStack(e.getKey()));
            }
        }

        // 写死规则（T11 B10）：烈焰人燃烧室只留给烟熏；再补内置声明的媒介
        result.removeIf(s -> isSuppressed(s.getItem(), typeId));
        for (Item builtIn : builtInMediaFor(typeId)) {
            if (isSuppressed(builtIn, typeId)) continue;
            if (seen.add(builtIn)) result.add(new ItemStack(builtIn));
        }

        return result;
    }

    // ================= 内置写死规则（T11 B10 定点修改） =================

    private static final ResourceLocation DP_FREEZING =
            ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "freezing");
    private static final ResourceLocation DP_ENDING =
            ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "ending");
    private static final ResourceLocation CREATE_SMOKING =
            ResourceLocation.fromNamespaceAndPath("create", "smoking");
    private static final ResourceLocation BLAZE_BURNER_ID =
            ResourceLocation.fromNamespaceAndPath("create", "blaze_burner");

    /**
     * <b>写死的内置媒介</b>：上游没提供触媒标签、但"拿这个物品当媒介"语义明确的情况。
     *
     * <ul>
     *   <li>{@code create_dragons_plus:freezing} ← <b>细雪桶</b>：龙+ 的批量冷冻靠方块状态判定
     *       （{@code #create_dragons_plus:passive_block_freezers}），<b>没有任何触媒标签</b>，
     *       不在这里补就永远没有可用媒介；</li>
     *   <li>{@code create_dragons_plus:ending} ← <b>龙首</b>：龙+ 的终结方块标签是空数组，
     *       自动发现只能从流体标签拿到龙息桶，龙首是这里补的。</li>
     * </ul>
     */
    private static final Map<ResourceLocation, List<ResourceLocation>> BUILT_IN_TYPE_MEDIA = Map.of(
            DP_FREEZING, List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "powder_snow_bucket")),
            DP_ENDING, List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "dragon_head")),
            ResourceLocation.fromNamespaceAndPath("create_more_catalysts", "snow_flow"),
            List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "snow_block")));

    /** 查 {@code create:blaze_burner}（惰性查注册表，避免静态初始化顺序问题）。 */
    @Nullable
    private static Item blazeBurnerItem() {
        Item item = BuiltInRegistries.ITEM.get(BLAZE_BURNER_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    /**
     * <b>写死的抑制规则</b>：烈焰人燃烧室<b>只能</b>当「烟熏」的媒介。
     *
     * <p>它同时被 {@code create:.../blasting}（熔炼）、{@code .../smoking}（烟熏）与 DnD 的
     * {@code dndesires:.../seething}（超级冶炼）收录，导致"手里拿着燃烧室"会意外触发熔炼/超级冶炼。
     * 按需求把它<b>留给烟熏</b>：熔炼与超级冶炼一律不读它。</p>
     */
    private static boolean isSuppressed(Item item, ResourceLocation typeId) {
        Item burner = blazeBurnerItem();
        return burner != null && item == burner && !CREATE_SMOKING.equals(typeId);
    }

    /** 查某个类型被写死声明的内置媒介物品。 */
    private static List<Item> builtInMediaFor(ResourceLocation typeId) {
        List<ResourceLocation> ids = BUILT_IN_TYPE_MEDIA.get(typeId);
        if (ids == null) return List.of();
        List<Item> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != Items.AIR) out.add(item);
        }
        return out;
    }

    // ================= 配置覆盖 + 冲突报告（T11 B5） =================

    /** 一条已生效的覆盖规则：{@code typeId == null} 表示"禁用该物品作为介质"。 */
    public record MediaTypeOverride(@Nullable ResourceLocation typeId) {
        public boolean disabled() {
            return typeId == null;
        }
    }

    /** 解析后的规则（按配置顺序，命中第一条即生效）。 */
    private record OverrideRule(ResourceLocation item, @Nullable ResourceLocation typeId) {}

    private static volatile List<OverrideRule> RULE_CACHE = List.of();
    /** 上一次解析用的原始配置内容，用于检测"配置变了"（内容相等即复用缓存，避免每 tick 重新解析）。 */
    private static volatile List<? extends String> RULE_SOURCE = List.of();

    /** 上一次冲突报告的签名，用于去重（避免启动期两次扫描把同一份报告打两遍）。 */
    private static volatile String CONFLICT_SIGNATURE = "";

    /**
     * 读配置并解析 {@code bellows_type_overrides}（带缓存）。
     *
     * <p>格式：{@code "<item_id>=<type_id>"} 锁死类型、{@code "<item_id>=none"} 禁用。
     * 非法行记 warn 并跳过；重复物品以<b>第一条</b>为准。</p>
     *
     * <p>读配置全程 try/catch：SERVER 配置在"客户端还没进世界"时 {@code get()} 会抛，
     * 而 {@link #effectiveTypes} 也可能在那种时点被调用（渲染/懒加载路径）。</p>
     */
    private static List<OverrideRule> rules() {
        List<? extends String> raw;
        try {
            if (!ServerConfig.SPEC.isLoaded()) return List.of();
            raw = ServerConfig.INSTANCE.bellowsTypeOverrides.get();
        } catch (Exception e) {
            return List.of();
        }
        if (raw.equals(RULE_SOURCE)) return RULE_CACHE;

        List<OverrideRule> parsed = new ArrayList<>();
        java.util.Set<ResourceLocation> disabledTypes = new java.util.LinkedHashSet<>();
        java.util.Map<ResourceLocation, Integer> typePriorities = new java.util.LinkedHashMap<>();
        Set<ResourceLocation> seenItems = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) continue;
            String line = entry.trim();
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid rule '{}' (use 'item_id=type_id')", entry);
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(line.substring(0, eq).trim());
            if (itemId == null) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid item id in '{}'", entry);
                continue;
            }
            if (isFanType(itemId)) {
                // ★ 类型级规则：<type_id>=none 整体禁用，<type_id>=<整数> 覆盖优先级
                String rhsType = line.substring(eq + 1).trim();
                if (rhsType.equalsIgnoreCase("none")) {
                    disabledTypes.add(itemId);
                } else {
                    try {
                        typePriorities.put(itemId, Integer.parseInt(rhsType));
                    } catch (NumberFormatException nfe) {
                        CreateHandMade.LOGGER.warn("bellows_type_overrides: type rule '{}' needs 'none' or an integer", entry);
                    }
                }
                continue;
            }
            if (BuiltInRegistries.ITEM.get(itemId) == Items.AIR) {
                // 常见坑：写的是方块 id 而不是物品 id（细雪方块的物品是 powder_snow_bucket）
                CreateHandMade.LOGGER.warn(
                        "bellows_type_overrides: '{}' is not a registered item (use the item/block-item id, "
                                + "e.g. the bucket for a fluid); rule ignored", itemId);
                continue;
            }
            if (!seenItems.add(itemId)) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: duplicate rule for {} ignored (first one wins)", itemId);
                continue;
            }

            String rhs = line.substring(eq + 1).trim();
            if (rhs.equalsIgnoreCase("none")) {
                parsed.add(new OverrideRule(itemId, null));
                continue;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(rhs);
            if (typeId == null) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid type id in '{}'", entry);
                continue;
            }
            if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(typeId) == null) {
                // 模组没装（这条规则是给可选兼容写的）⇒ 只记 debug，别用 WARN 刷屏；
                // 模组装了却没有这个类型 ⇒ 才是真的写错，用 WARN。
                if (ModList.get().isLoaded(typeId.getNamespace())) {
                    CreateHandMade.LOGGER.warn(
                            "bellows_type_overrides: unknown fan type {} in '{}' (ignored)", typeId, entry);
                } else {
                    CreateHandMade.LOGGER.debug(
                            "bellows_type_overrides: skipping '{}' (mod '{}' is not installed)",
                            entry, typeId.getNamespace());
                }
                continue;
            }
            parsed.add(new OverrideRule(itemId, typeId));
        }

        DISABLED_TYPES = java.util.Set.copyOf(disabledTypes);
        TYPE_PRIORITY = java.util.Map.copyOf(typePriorities);
        RULE_CACHE = List.copyOf(parsed);
        RULE_SOURCE = raw;
        if (!parsed.isEmpty()) {
            CreateHandMade.LOGGER.info("bellows media: {} type override rule(s) active", parsed.size());
        }
        return RULE_CACHE;
    }

    /** 查一个物品是否被配置指定了"优先类型"或被禁用；没配返回 {@code null}。 */
    @Nullable
    public static MediaTypeOverride overrideFor(Item item) {
        for (OverrideRule rule : rules()) {
            if (rule.item().equals(BuiltInRegistries.ITEM.getKey(item))) {
                return new MediaTypeOverride(rule.typeId());
            }
        }
        return null;
    }

    /** 配置是否允许"该物品 → 该类型"这条路径（供 JEI 列表保持一致）。 */
    private static boolean allowedByRules(Item item, ResourceLocation typeId) {
        MediaTypeOverride override = overrideFor(item);
        if (override == null) return true;
        // B8 起 "item=type" 只是"优先"，不是"独占"：该物品照样能给别的类型当回落，
        // 只有 "item=none" 才真的把它从介质列表里去掉。
        return !override.disabled();
    }

    /**
     * 解析"这个物品能用哪些鼓风类型"（T11 B5/B8）。
     *
     * @param ids       候选类型 id（数据包优先、自动发现补后；被 {@code none} 禁用时为空）
     * @param preferred 配置里指定的<b>优先</b>类型（{@code null} = 没配），选中时无视优先级
     */
    public record EffectiveTypes(List<ResourceLocation> ids, @Nullable ResourceLocation preferred) {}

    /**
     * 应用配置后的候选类型（<b>吐介质判定专用</b>）。
     *
     * <ul>
     *   <li>没配覆盖：候选 = {@link #resolveAll}，没有优先项；</li>
     *   <li>{@code none}：候选为空 ⇒ 这把风箱对该物品完全失效；</li>
     *   <li>{@code item=某类型}：候选<b>不变</b>（保留其它类型做回落），只把该类型标成"优先"，
     *       使用方在比优先级时让它直接胜出 —— 这就是 B8 的语义修正。</li>
     * </ul>
     *
     * <p><b>为什么从"锁定"改成"优先"：</b>B5 的实现是"只剩这一个候选"，于是把
     * {@code create:blaze_burner=dndesires:seething} 写进默认值后，烈焰人燃烧室就再也做不了
     * 原版烟熏/熔炼了（DnD 吃不下时没有回落）。改成"优先 + 回落"后：
     * 有 DnD 配方就用 DnD，没有就照旧回落 Create 的类型 —— 两种诉求都满足。</p>
     *
     * <p>为什么不在 {@link #resolveAll} 里做：{@link #isMedia} / 渲染器用它判断
     * "手上是不是拿着介质"，那属于物理事实，不该被加工策略改变。</p>
     */
    public static EffectiveTypes resolveEffective(ItemStack stack) {
        if (stack.isEmpty()) return new EffectiveTypes(List.of(), null);
        List<ResourceLocation> base = resolveAll(stack);
        MediaTypeOverride override = overrideFor(stack.getItem());
        if (override == null) return new EffectiveTypes(base, null);
        if (override.disabled()) return new EffectiveTypes(List.of(), null);

        // ★ T11 B9：优先类型若本来<b>不在</b>候选里，必须注入进去 —— 否则像
        //   "minecraft:dragon_head=create_dragons_plus:ending" 这种"物品没被该类型标签收录"
        //   的规则会完全不起作用（龙+ 的 ending 标签是空的，只有流体标签）。
        //   注入后它排在最前，其余候选保留做回落。
        ResourceLocation preferred = override.typeId();
        if (base.contains(preferred)) return new EffectiveTypes(base, preferred);

        List<ResourceLocation> withPreferred = new ArrayList<>(base.size() + 1);
        withPreferred.add(preferred);
        withPreferred.addAll(base);
        return new EffectiveTypes(List.copyOf(withPreferred), preferred);
    }

    /**
     * 冲突报告（T11 B5）：同一种物品被多个类型认领时打 WARN，并指出配置里是否已经处理。
     *
     * <p>合并两个来源：数据包绑定（原料按 {@code getItems()} 展开）与自动发现的 {@link #AUTO_MAP}。
     * 报告按物品 id 排序、内容不变则不重复打印（启动期可能被扫描两次）。</p>
     */
    private static void reportConflicts() {
        // 配置还没加载就先不报：那时读不到 bellows_type_overrides，会打印出"没有配置注解"的半成品，
        // 紧接着配置加载完又要再打一遍。等配置就绪后再报（服务端启动一定来得及）。
        if (!ServerConfig.SPEC.isLoaded()) return;

        Map<Item, List<ResourceLocation>> byItem = new LinkedHashMap<>();
        for (Binding binding : DATA_ENTRIES) {
            if (binding.item() != null) {
                collectType(byItem, binding.item(), binding.typeId());
            } else if (binding.ingredient() != null) {
                for (ItemStack stack : binding.ingredient().getItems()) {
                    if (!stack.isEmpty()) collectType(byItem, stack.getItem(), binding.typeId());
                }
            }
        }
        for (Map.Entry<Item, List<ResourceLocation>> e : AUTO_MAP.entrySet()) {
            for (ResourceLocation typeId : e.getValue()) {
                collectType(byItem, e.getKey(), typeId);
            }
        }

        List<Item> conflicts = new ArrayList<>();
        for (Map.Entry<Item, List<ResourceLocation>> e : byItem.entrySet()) {
            if (e.getValue().size() > 1) conflicts.add(e.getKey());
        }
        conflicts.sort(Comparator.comparing(item -> String.valueOf(BuiltInRegistries.ITEM.getKey(item))));

        // 签名 = 冲突表 + 每条冲突当前的配置处置，配置变了才重新打印
        StringBuilder signature = new StringBuilder();
        for (Item item : conflicts) {
            MediaTypeOverride override = overrideFor(item);
            signature.append(item).append('=').append(byItem.get(item))
                    .append(override == null ? "" : "|" + override.typeId()).append(';');
        }
        String sig = signature.toString();
        if (sig.equals(CONFLICT_SIGNATURE)) return;
        CONFLICT_SIGNATURE = sig;

        if (conflicts.isEmpty()) {
            CreateHandMade.LOGGER.debug("bellows media: no catalyst conflicts detected");
            return;
        }

        CreateHandMade.LOGGER.warn("Found catalyst conflicts for {} item(s) (one item claimed by multiple fan types):",
                conflicts.size());
        for (Item item : conflicts) {
            MediaTypeOverride override = overrideFor(item);
            String note = override == null
                    ? ""
                    : (override.disabled()
                            ? "  [disabled by bellows_type_overrides]"
                            : "  [prefers " + override.typeId() + " by bellows_type_overrides]");
            CreateHandMade.LOGGER.warn(" - {}: {}{}", BuiltInRegistries.ITEM.getKey(item), byItem.get(item), note);
        }
        CreateHandMade.LOGGER.warn(
                "Please configure 'tools.bellows.bellows_type_overrides' in create_hand_made-server.toml "
                        + "('item_id=type_id' or 'item_id=none') to resolve.");
    }

    private static void collectType(Map<Item, List<ResourceLocation>> byItem, Item item, ResourceLocation typeId) {
        if (isSuppressed(item, typeId)) return;   // 写死抑制的类型不计入冲突报告
        List<ResourceLocation> ids = byItem.computeIfAbsent(item, k -> new ArrayList<>());
        if (!ids.contains(typeId)) ids.add(typeId);
    }
}

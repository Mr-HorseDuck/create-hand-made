package com.alben.createhandmade.bellows;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 风箱的「副手介质」注册表：把<b>物品</b>映射到<b>鼓风类型的 id</b>。
 *
 * <p>1.20.1 版：内部用 {@link ResourceLocation} 存类型，调用方通过
 * {@code CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id)} 动态取类型对象，
 * 因此任何注册进 Create 注册表的第三方鼓风类型都能被表达。</p>
 *
 * <h3>数据来源</h3>
 * <ol>
 *   <li>数据包 {@code data/<ns>/bellows_media/*.json}：{@code ingredients} / {@code catalysts} / {@code fluids} / {@code priority}</li>
 *   <li>自动发现：扫所有 {@code fan_processing_catalysts/<type>} / {@code fan_catalysts/<type>} 标签</li>
 *   <li>id 规律：龙+ 染料桶、创意传动催化剂</li>
 *   <li>内置写死：细雪桶→freezing、龙首→ending、烈焰人燃烧室只做烟熏</li>
 * </ol>
 */
public class BellowsMediaRegistry {

    /** 一条展开后的绑定：要么是 Ingredient，要么是具体 Item。 */
    private record Binding(ResourceLocation typeId, @Nullable Ingredient ingredient, @Nullable Item item) {
        boolean matches(ItemStack stack) {
            if (item != null) return stack.getItem() == item;
            return ingredient != null && ingredient.test(stack);
        }
    }

    private static volatile List<Binding> DATA_ENTRIES = List.of();
    private static volatile Map<ResourceLocation, List<Fluid>> DATA_FLUID_MEDIA = Map.of();
    private static volatile Map<ResourceLocation, List<Fluid>> AUTO_FLUID_MEDIA = Map.of();
    private static volatile Map<ResourceLocation, Integer> PRIORITY_OVERRIDES = Map.of();
    private static volatile Map<Item, List<ResourceLocation>> AUTO_MAP = Map.of();

    // ================= 内置写死规则 =================

    private static final ResourceLocation DP_FREEZING =
            new ResourceLocation("create_dragons_plus", "freezing");
    private static final ResourceLocation DP_ENDING =
            new ResourceLocation("create_dragons_plus", "ending");
    private static final ResourceLocation CREATE_SMOKING =
            new ResourceLocation("create", "smoking");
    private static final ResourceLocation BLAZE_BURNER_ID =
            new ResourceLocation("create", "blaze_burner");

    /** 细雪桶 → 龙+ freezing；龙首 → 龙+ ending。 */
    private static final Map<ResourceLocation, List<ResourceLocation>> BUILT_IN_TYPE_MEDIA = Map.of(
            DP_FREEZING, List.of(new ResourceLocation("minecraft", "powder_snow_bucket")),
            DP_ENDING, List.of(new ResourceLocation("minecraft", "dragon_head"))
    );

    /** 龙+ 染料桶后缀。 */
    private static final String DYE_BUCKET_SUFFIX = "_dye_bucket";
    /** 创意传动催化剂后缀。 */
    private static final String CREATIVE_TRANSMISSION_SUFFIX = "_fan_dyeing_catalyst";
    /** 染料桶 id 的主干前缀（龙+ 实测带 coloring_ 前缀）。 */
    private static final List<String> DYE_TYPE_PREFIXES = List.of("", "coloring_");
    /** 触媒标签前缀。 */
    private static final List<String> CATALYST_TAG_PREFIXES =
            List.of("fan_processing_catalysts/", "fan_catalysts/");

    // ================= 数据包重载 =================

    public static void reloadData(Collection<BellowsMediaEntry> files) {
        List<Binding> flat = new ArrayList<>();
        Map<ResourceLocation, Integer> priorities = new HashMap<>();
        Map<ResourceLocation, List<Fluid>> dataFluids = new LinkedHashMap<>();
        int withCatalysts = 0;
        int tagItems = 0;
        int fluidOnly = 0;

        for (BellowsMediaEntry file : files) {
            ResourceLocation typeId = file.processingTypeId();

            // priority
            file.priority().ifPresent(priority -> {
                Integer previous = priorities.putIfAbsent(typeId, priority);
                if (previous != null && !previous.equals(priority)) {
                    CreateHandMade.LOGGER.warn(
                            "bellows_media: conflicting priority for {} ({} vs {}); using the larger value",
                            typeId, previous, priority);
                    priorities.put(typeId, Math.max(previous, priority));
                }
            });

            // ingredients
            for (Ingredient ing : file.ingredients()) {
                flat.add(new Binding(typeId, ing, null));
            }

            // catalysts
            file.catalysts().ifPresent(rawTag -> {
                // 这里用局部变量是为了配合 lambda
            });
            Optional<String> rawTag = file.catalysts();
            if (rawTag.isPresent()) {
                withCatalysts++;
                ResourceLocation tagId = parseTagId(rawTag.get());
                if (tagId != null) {
                    Set<Item> items = itemsInTag(tagId);
                    if (items.isEmpty()) {
                        CreateHandMade.LOGGER.debug(
                                "bellows_media: catalysts tag {} expanded to 0 items", tagId);
                    }
                    for (Item item : items) {
                        flat.add(new Binding(typeId, null, item));
                        tagItems++;
                    }
                }
            }

            // fluids
            for (ResourceLocation fluidId : file.fluids()) {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluid == null || fluid == Fluids.EMPTY) {
                    CreateHandMade.LOGGER.warn(
                            "bellows_media: unknown fluid {} for type {} (ignored)", fluidId, typeId);
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

        DATA_ENTRIES = List.copyOf(flat);
        PRIORITY_OVERRIDES = Map.copyOf(priorities);
        DATA_FLUID_MEDIA = freezeFluids(dataFluids);
        CreateHandMade.LOGGER.info(
                "Loaded {} bellows media entries ({} with catalysts, {} catalyst items, {} fluid media "
                        + "({} without bucket item), {} priority overrides)",
                files.size(), withCatalysts, tagItems,
                dataFluids.values().stream().mapToInt(List::size).sum(), fluidOnly, priorities.size());
    }

    // ================= 自动发现 =================

    public static void autoDiscover() {
        Map<Item, List<ResourceLocation>> map = new LinkedHashMap<>();
        Map<ResourceLocation, List<Fluid>> fluids = new LinkedHashMap<>();

        // ① Create 4 组内置标签（兼容旧行为）
        scanTag(BuiltInRegistries.BLOCK.getTagOrEmpty(
                        AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_BLASTING.tag),
                new ResourceLocation("create", "blasting"), map);
        scanTag(BuiltInRegistries.BLOCK.getTagOrEmpty(
                        AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SMOKING.tag),
                new ResourceLocation("create", "smoking"), map);
        scanTag(BuiltInRegistries.BLOCK.getTagOrEmpty(
                        AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_HAUNTING.tag),
                new ResourceLocation("create", "haunting"), map);
        scanTag(BuiltInRegistries.BLOCK.getTagOrEmpty(
                        AllTags.AllBlockTags.FAN_PROCESSING_CATALYSTS_SPLASHING.tag),
                new ResourceLocation("create", "splashing"), map);

        // ② 泛化扫描所有第三方触媒标签
        scanCatalystTags(map, fluids);

        // ③ id 规律识别
        scanItemPatterns(map);

        Map<Item, List<ResourceLocation>> frozen = new HashMap<>();
        map.forEach((item, ids) -> frozen.put(item, List.copyOf(ids)));
        AUTO_MAP = Map.copyOf(frozen);
        AUTO_FLUID_MEDIA = freezeFluids(fluids);

        // 诊断
        if (Config.INSTANCE.debugEnabled.get()) {
            Map<String, Integer> perType = new java.util.TreeMap<>();
            map.forEach((item, ids) -> ids.forEach(id -> {
                if (isSuppressed(item, id)) return;
                perType.merge(id.toString(), 1, Integer::sum);
            }));
            CreateHandMade.LOGGER.info("[HandMade-Debug] bellows media by fan type: {}", perType);
            List<String> withoutMedia = new ArrayList<>();
            for (ResourceLocation id : CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet()) {
                if (!perType.containsKey(id.toString())) withoutMedia.add(id.toString());
            }
            CreateHandMade.LOGGER.info("[HandMade-Debug] fan types without any bellows media: {}", withoutMedia);
        }

        reportConflicts();
    }

    private static void scanTag(Iterable<Holder<Block>> holders, ResourceLocation typeId,
                                Map<Item, List<ResourceLocation>> map) {
        for (Holder<Block> h : holders) {
            Block block = h.value();
            // 烈焰人燃烧室只给烟熏，不给熔炼
            if (block == AllBlocks.BLAZE_BURNER.get() && !CREATE_SMOKING.equals(typeId)) {
                continue;
            }
            Item item = itemForBlock(block);
            if (item != Items.AIR) addTypeId(map, item, typeId);
        }
    }

    /** 遍历所有 block/item/fluid 标签，名字像触媒的展开。 */
    private static void scanCatalystTags(Map<Item, List<ResourceLocation>> map,
                                         Map<ResourceLocation, List<Fluid>> fluids) {
        int tagCount = 0;
        int itemCountBefore = map.size();
        int noItemBlocks = 0;
        int bucketlessFluids = 0;

        // block tags
        for (TagKey<Block> tagKey : ForgeRegistries.BLOCKS.tags().getTagNames().toList()) {
    ResourceLocation tagId = tagKey.location();
    ResourceLocation typeId = catalystTypeIdFor(tagId);
    if (typeId == null) continue;
    if (!isRegisteredFanType(typeId)) continue;
    tagCount++;

    for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey)) {
        Item item = itemForBlock(h.value());
        if (item == Items.AIR) {
            noItemBlocks++;
            continue;
        }
        addTypeId(map, item, typeId);
    }
}

        // item tags
        for (TagKey<Item> tagKey : ForgeRegistries.ITEMS.tags().getTagNames().toList()) {
    ResourceLocation tagId = tagKey.location();
    ResourceLocation typeId = catalystTypeIdFor(tagId);
    if (typeId == null) continue;
    if (!isRegisteredFanType(typeId)) continue;
    tagCount++;

    for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
        if (h.value() != Items.AIR) addTypeId(map, h.value(), typeId);
    }
}

        // fluid tags
        for (TagKey<Fluid> tagKey : ForgeRegistries.FLUIDS.tags().getTagNames().toList()) {
    ResourceLocation tagId = tagKey.location();
    ResourceLocation typeId = catalystTypeIdFor(tagId);
    if (typeId == null) continue;
    if (!isRegisteredFanType(typeId)) continue;
    tagCount++;

    for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(tagKey)) {
        Item bucket = h.value().getBucket();
        if (bucket != Items.AIR) {
            addTypeId(map, bucket, typeId);
        } else {
            bucketlessFluids++;
            addFluid(fluids, typeId, h.value());
        }
    }
}
    }

    /** 按 id 后缀识别染料桶。 */
    private static void scanItemPatterns(Map<Item, List<ResourceLocation>> map) {
        int matched = 0;
        for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
            String path = itemId.getPath();
            String suffix = null;
            if (path.endsWith(DYE_BUCKET_SUFFIX) && path.length() > DYE_BUCKET_SUFFIX.length()) {
                suffix = DYE_BUCKET_SUFFIX;
            } else if (path.endsWith(CREATIVE_TRANSMISSION_SUFFIX)
                    && path.length() > CREATIVE_TRANSMISSION_SUFFIX.length()) {
                suffix = CREATIVE_TRANSMISSION_SUFFIX;
            }
            if (suffix == null) continue;

            String variant = path.substring(0, path.length() - suffix.length());
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) continue;

            boolean bound = false;
            for (String prefix : DYE_TYPE_PREFIXES) {
                ResourceLocation typeId = new ResourceLocation(itemId.getNamespace(), prefix + variant);
                if (!isRegisteredFanType(typeId)) continue;
                addTypeId(map, item, typeId);
                bound = true;
                break;
            }
            if (!bound) {
                ResourceLocation dpType = new ResourceLocation("create_dragons_plus", "coloring_" + variant);
                if (isRegisteredFanType(dpType)) {
                    addTypeId(map, item, dpType);
                    bound = true;
                }
            }
            if (bound) matched++;
        }
        if (matched > 0) {
            CreateHandMade.LOGGER.info(
                    "Auto-discovered {} dye catalyst item(s) via id-suffix rule", matched);
        }
    }

    // ================= 解析 API =================

    /** 旧 API：返回优先级最高的可用类型。 */
    @Nullable
    public static FanProcessingType resolve(ItemStack stack) {
        EffectiveTypes eff = resolveEffective(stack);
        if (eff.ids().isEmpty()) return null;
        if (eff.preferred() != null) {
            FanProcessingType preferred = getType(eff.preferred());
            if (preferred != null) return preferred;
        }
        FanProcessingType best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (ResourceLocation id : eff.ids()) {
            FanProcessingType type = getType(id);
            if (type == null) continue;
            int p = getPriority(type, id);
            if (p > bestPriority) {
                best = type;
                bestPriority = p;
            }
        }
        return best;
    }

    /** 所有候选 typeId（去重保序）。 */
    public static List<ResourceLocation> resolveAll(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Binding b : DATA_ENTRIES) {
            if (b.matches(stack)) ids.add(b.typeId());
        }

        ensureAutoDiscovered();
        List<ResourceLocation> auto = AUTO_MAP.get(stack.getItem());
        if (auto != null) ids.addAll(auto);

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

    public record EffectiveTypes(List<ResourceLocation> ids, @Nullable ResourceLocation preferred) {}

    public static EffectiveTypes resolveEffective(ItemStack stack) {
        if (stack.isEmpty()) return new EffectiveTypes(List.of(), null);
        List<ResourceLocation> base = resolveAll(stack);
        MediaTypeOverride override = overrideFor(stack.getItem());
        if (override == null) return new EffectiveTypes(base, null);
        if (override.disabled()) return new EffectiveTypes(List.of(), null);

        ResourceLocation preferred = override.typeId();
        if (base.contains(preferred)) return new EffectiveTypes(base, preferred);

        List<ResourceLocation> withPreferred = new ArrayList<>(base.size() + 1);
        withPreferred.add(preferred);
        withPreferred.addAll(base);
        return new EffectiveTypes(List.copyOf(withPreferred), preferred);
    }

    /** 旧 API：按类型对象反查介质。 */
    public static List<ItemStack> getMediaFor(FanProcessingType type) {
        ResourceLocation typeId = getTypeId(type);
        if (typeId == null) return List.of();
        return getMediaFor(typeId);
    }

    /** 新 API：按 typeId 反查介质。 */
    public static List<ItemStack> getMediaFor(ResourceLocation typeId) {
        ensureAutoDiscovered();

        List<ItemStack> result = new ArrayList<>();
        Set<Item> seen = new HashSet<>();

        for (Binding b : DATA_ENTRIES) {
            if (!b.typeId().equals(typeId)) continue;
            if (b.item() != null) {
                if (!allowedByRules(b.item(), typeId)) continue;
                if (seen.add(b.item())) result.add(new ItemStack(b.item()));
            } else if (b.ingredient() != null) {
                for (ItemStack s : b.ingredient().getItems()) {
                    if (s.isEmpty()) continue;
                    if (!allowedByRules(s.getItem(), typeId)) continue;
                    if (seen.add(s.getItem())) result.add(s.copy());
                }
            }
        }

        for (Map.Entry<Item, List<ResourceLocation>> e : AUTO_MAP.entrySet()) {
            if (!e.getValue().contains(typeId)) continue;
            if (!allowedByRules(e.getKey(), typeId)) continue;
            if (seen.add(e.getKey())) result.add(new ItemStack(e.getKey()));
        }

        result.removeIf(s -> isSuppressed(s.getItem(), typeId));
        for (Item builtIn : builtInMediaFor(typeId)) {
            if (isSuppressed(builtIn, typeId)) continue;
            if (seen.add(builtIn)) result.add(new ItemStack(builtIn));
        }

        return result;
    }

    public static boolean isMedia(ItemStack stack) {
        return !resolveAll(stack).isEmpty();
    }

    // ================= 优先级 / 配置 =================

    @Nullable
    public static Integer priorityOverride(ResourceLocation typeId) {
        return PRIORITY_OVERRIDES.get(typeId);
    }

    @Nullable
    public static Integer typePriorityOverride(ResourceLocation typeId) {
        return TYPE_PRIORITY.get(typeId);
    }

    public static boolean isTypeDisabled(ResourceLocation typeId) {
        return DISABLED_TYPES.contains(typeId);
    }

    private static int getPriority(FanProcessingType type, ResourceLocation typeId) {
        Integer dataOverride = PRIORITY_OVERRIDES.get(typeId);
        if (dataOverride != null) return dataOverride;
        Integer configOverride = TYPE_PRIORITY.get(typeId);
        if (configOverride != null) return configOverride;
        return type.getPriority();
    }

    // ================= 配置覆盖 =================

    public record MediaTypeOverride(@Nullable ResourceLocation typeId) {
        public boolean disabled() { return typeId == null; }
    }

    private record OverrideRule(ResourceLocation item, @Nullable ResourceLocation typeId) {}

    private static volatile List<OverrideRule> RULE_CACHE = List.of();
    private static volatile List<? extends String> RULE_SOURCE = List.of();
    private static volatile Set<ResourceLocation> DISABLED_TYPES = Set.of();
    private static volatile Map<ResourceLocation, Integer> TYPE_PRIORITY = Map.of();
    private static volatile String CONFLICT_SIGNATURE = "";

    private static List<OverrideRule> rules() {
        List<? extends String> raw;
        try {
            raw = Config.INSTANCE.bellowsTypeOverrides.get();
        } catch (Exception e) {
            return List.of();
        }
        if (raw.equals(RULE_SOURCE)) return RULE_CACHE;

        List<OverrideRule> parsed = new ArrayList<>();
        Set<ResourceLocation> disabledTypes = new LinkedHashSet<>();
        Map<ResourceLocation, Integer> typePriorities = new LinkedHashMap<>();
        Set<ResourceLocation> seenItems = new LinkedHashSet<>();

        for (String entry : raw) {
            if (entry == null) continue;
            String line = entry.trim();
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid rule '{}' (use 'item_id=type_id')", entry);
                continue;
            }
            ResourceLocation leftId = ResourceLocation.tryParse(line.substring(0, eq).trim());
            if (leftId == null) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid id in '{}'", entry);
                continue;
            }

            // 类型级规则
            if (isRegisteredFanType(leftId)) {
                String rhs = line.substring(eq + 1).trim();
                if (rhs.equalsIgnoreCase("none")) {
                    disabledTypes.add(leftId);
                } else {
                    try {
                        typePriorities.put(leftId, Integer.parseInt(rhs));
                    } catch (NumberFormatException nfe) {
                        CreateHandMade.LOGGER.warn("bellows_type_overrides: type rule '{}' needs 'none' or an integer", entry);
                    }
                }
                continue;
            }

            // 物品级规则
            if (ForgeRegistries.ITEMS.getValue(leftId) == null) {
                CreateHandMade.LOGGER.warn(
                        "bellows_type_overrides: '{}' is not a registered item; rule ignored", leftId);
                continue;
            }
            if (!seenItems.add(leftId)) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: duplicate rule for {} ignored", leftId);
                continue;
            }

            String rhs = line.substring(eq + 1).trim();
            if (rhs.equalsIgnoreCase("none")) {
                parsed.add(new OverrideRule(leftId, null));
                continue;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(rhs);
            if (typeId == null) {
                CreateHandMade.LOGGER.warn("bellows_type_overrides: invalid type id in '{}'", entry);
                continue;
            }
            if (!isRegisteredFanType(typeId)) {
                if (ModList.get().isLoaded(typeId.getNamespace())) {
                    CreateHandMade.LOGGER.warn(
                            "bellows_type_overrides: unknown fan type {} in '{}' (ignored)", typeId, entry);
                } else {
                    CreateHandMade.LOGGER.debug(
                            "bellows_type_overrides: skipping '{}' (mod '{}' not installed)",
                            entry, typeId.getNamespace());
                }
                continue;
            }
            parsed.add(new OverrideRule(leftId, typeId));
        }

        DISABLED_TYPES = Set.copyOf(disabledTypes);
        TYPE_PRIORITY = Map.copyOf(typePriorities);
        RULE_CACHE = List.copyOf(parsed);
        RULE_SOURCE = raw;
        return RULE_CACHE;
    }

    @Nullable
    public static MediaTypeOverride overrideFor(Item item) {
        for (OverrideRule rule : rules()) {
            if (rule.item().equals(ForgeRegistries.ITEMS.getKey(item))) {
                return new MediaTypeOverride(rule.typeId());
            }
        }
        return null;
    }

    private static boolean allowedByRules(Item item, ResourceLocation typeId) {
        MediaTypeOverride override = overrideFor(item);
        if (override == null) return true;
        return !override.disabled();
    }

    // ================= 冲突诊断 =================

    private static void reportConflicts() {
        Map<Item, List<ResourceLocation>> byItem = new LinkedHashMap<>();
        for (Binding b : DATA_ENTRIES) {
            if (b.item() != null) {
                collectType(byItem, b.item(), b.typeId());
            } else if (b.ingredient() != null) {
                for (ItemStack s : b.ingredient().getItems()) {
                    if (!s.isEmpty()) collectType(byItem, s.getItem(), b.typeId());
                }
            }
        }
        for (Map.Entry<Item, List<ResourceLocation>> e : AUTO_MAP.entrySet()) {
            for (ResourceLocation id : e.getValue()) collectType(byItem, e.getKey(), id);
        }

        List<Item> conflicts = new ArrayList<>();
        for (Map.Entry<Item, List<ResourceLocation>> e : byItem.entrySet()) {
            if (e.getValue().size() > 1) conflicts.add(e.getKey());
        }
        conflicts.sort((a, b) -> {
            ResourceLocation ka = ForgeRegistries.ITEMS.getKey(a);
            ResourceLocation kb = ForgeRegistries.ITEMS.getKey(b);
            return String.valueOf(ka).compareTo(String.valueOf(kb));
        });

        StringBuilder sig = new StringBuilder();
        for (Item item : conflicts) {
            MediaTypeOverride override = overrideFor(item);
            sig.append(item).append('=').append(byItem.get(item))
                    .append(override == null ? "" : "|" + override.typeId()).append(';');
        }
        String s = sig.toString();
        if (s.equals(CONFLICT_SIGNATURE)) return;
        CONFLICT_SIGNATURE = s;

        if (conflicts.isEmpty()) return;

        CreateHandMade.LOGGER.warn("Found catalyst conflicts for {} item(s):", conflicts.size());
        for (Item item : conflicts) {
            MediaTypeOverride override = overrideFor(item);
            String note = override == null ? ""
                    : (override.disabled() ? "  [disabled]" : "  [prefers " + override.typeId() + "]");
            CreateHandMade.LOGGER.warn(" - {}: {}{}",
                    ForgeRegistries.ITEMS.getKey(item), byItem.get(item), note);
        }
        CreateHandMade.LOGGER.warn(
                "Please configure 'tools.bellows.bellows_type_overrides' in create_hand_made-server.toml");
    }

    private static void collectType(Map<Item, List<ResourceLocation>> map, Item item, ResourceLocation typeId) {
        if (isSuppressed(item, typeId)) return;
        map.computeIfAbsent(item, k -> new ArrayList<>()).add(typeId);
    }

    // ================= 内置写死规则 =================

    @Nullable
    private static Item blazeBurnerItem() {
        Item item = ForgeRegistries.ITEMS.getValue(BLAZE_BURNER_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    private static boolean isSuppressed(Item item, ResourceLocation typeId) {
        Item burner = blazeBurnerItem();
        return burner != null && item == burner && !CREATE_SMOKING.equals(typeId);
    }

    private static List<Item> builtInMediaFor(ResourceLocation typeId) {
        List<ResourceLocation> ids = BUILT_IN_TYPE_MEDIA.get(typeId);
        if (ids == null) return List.of();
        List<Item> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) out.add(item);
        }
        return out;
    }

    // ================= 工具方法 =================

    @Nullable
    private static ResourceLocation parseTagId(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.isEmpty()) {
            CreateHandMade.LOGGER.warn("bellows_media: empty catalysts tag '{}'", raw);
            return null;
        }
        return ResourceLocation.tryParse(s);
    }

    private static Set<Item> itemsInTag(ResourceLocation tagId) {
        Set<Item> items = new LinkedHashSet<>();
        for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(
                TagKey.create(Registries.BLOCK, tagId))) {
            Item item = itemForBlock(h.value());
            if (item != Items.AIR) items.add(item);
        }
        for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(
                TagKey.create(Registries.ITEM, tagId))) {
            if (h.value() != Items.AIR) items.add(h.value());
        }
        for (Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(
                TagKey.create(Registries.FLUID, tagId))) {
            Item bucket = h.value().getBucket();
            if (bucket != Items.AIR) items.add(bucket);
        }
        return items;
    }

    private static Item itemForBlock(Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) return item;
        return Item.BY_BLOCK.getOrDefault(block, Items.AIR);
    }

    @Nullable
    private static ResourceLocation catalystTypeIdFor(ResourceLocation tagId) {
        String path = tagId.getPath();
        for (String prefix : CATALYST_TAG_PREFIXES) {
            if (!path.startsWith(prefix)) continue;
            String typeName = path.substring(prefix.length());
            if (typeName.isEmpty() || typeName.indexOf('/') >= 0) return null;
            return new ResourceLocation(tagId.getNamespace(), typeName);
        }
        return null;
    }

    private static void addTypeId(Map<Item, List<ResourceLocation>> map, Item item, ResourceLocation typeId) {
        List<ResourceLocation> ids = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (!ids.contains(typeId)) ids.add(typeId);
    }

    private static void addFluid(Map<ResourceLocation, List<Fluid>> map, ResourceLocation typeId, Fluid fluid) {
        List<Fluid> list = map.computeIfAbsent(typeId, k -> new ArrayList<>());
        if (!list.contains(fluid)) list.add(fluid);
    }

    private static Map<ResourceLocation, List<Fluid>> freezeFluids(Map<ResourceLocation, List<Fluid>> map) {
        Map<ResourceLocation, List<Fluid>> frozen = new HashMap<>();
        map.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Map.copyOf(frozen);
    }

    private static boolean isRegisteredFanType(ResourceLocation id) {
        return CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id) != null;
    }

    @Nullable
    private static FanProcessingType getType(ResourceLocation id) {
        return CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id);
    }

    @Nullable
    private static ResourceLocation getTypeId(FanProcessingType type) {
        if (type == null) return null;
        for (ResourceLocation id : CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet()) {
            if (CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id) == type) return id;
        }
        return null;
    }

    private static void ensureAutoDiscovered() {
        if (AUTO_MAP.isEmpty()) autoDiscover();
    }

    public static List<Fluid> getFluidsFor(ResourceLocation typeId) {
        Set<Fluid> out = new LinkedHashSet<>();
        out.addAll(DATA_FLUID_MEDIA.getOrDefault(typeId, List.of()));
        out.addAll(AUTO_FLUID_MEDIA.getOrDefault(typeId, List.of()));
        return List.copyOf(out);
    }
}

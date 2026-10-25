package com.alben.createhandmade;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 服务端权威配置（{@code config/create_hand_made-server.toml}）：
 * <b>所有工具</b>的时间参数与开关，按两级分组 {@code [tools] → [tools.<tool>]} 组织。
 *
 * <h3>为什么是 SERVER 类型</h3>
 * <p>这些参数必须让<b>服务端与客户端读到同一个值</b>：</p>
 * <ul>
 *   <li>服务端用起手时长决定"什么时候开始出音效/粒子"，客户端用同一个值算视觉起手进度与加工相位；</li>
 *   <li>加工时长（加上"会话首次"的起手时长）决定了这次使用的总时长（{@code getUseDuration}）
 *       ——两端不一致会直接 Use 计时失步。</li>
 * </ul>
 * <p>若放 COMMON，两端各自读本地 toml，一旦不一致就会静默脱钩。SERVER 类型的同步行为
 * （NeoForge 21.1.250 实测）：专用服务器在<b>连接时</b>把本文件字节通过 {@code neoforge:config_file}
 * 包发给客户端，客户端在内存里替换本地值；<b>同步只发生在连接时</b>，改完要让已连接的客户端重连。
 * 单人模式同进程同文件，天然一致。</p>
 * <p>另一个好处：可以用存档级覆盖（{@code <存档>/serverconfig/create_hand_made-server.toml}）
 * 给单个存档单独设定节奏。</p>
 * <p>⚠ 客户端在<b>未进入世界时</b>（标题界面）SERVER 配置还没加载，此时 {@code get()} 会抛异常 ——
 * 所以<b>只有服务端路径</b>或"玩家已存在"的渲染路径才能读它。目前所有读取点都满足这一点
 * （例：{@code enable_tree_felling} 的两处读取都在 {@code level.isClientSide} 早退之后）。</p>
 *
 * <h3>分组与界面</h3>
 * <p>两级：{@code [tools]} 下每个工具有自己的组（手锯 / 研钵 / 碾钵 / 搅拌杖 / 冲压锤 / 风箱 / 灌注枪）。
 * 键名<b>不带工具前缀</b>（{@code tools.mortar.windup_ticks}，而不是
 * {@code tools.mortar.mortar_windup_ticks}）—— 组名已经提供了限定。指杆没有时间参数，不建组。</p>
 * <p><b>组名与组注释必须写在 {@code push()} 之前</b>：{@code ModConfigSpec.Builder#push} 会把当前
 * context 上挂的 comment / translation 记到"新路径"（也就是这个 section）上，push 之后再写就会被
 * 当成下面第一个键的属性。</p>
 *
 * <h3>本批次（T10 批次 1）的范围</h3>
 * <p>只建结构：所有键<b>以当前硬编码值为默认值</b>建立，工具暂时仍读各自的硬编码常量
 * （唯一例外是 {@code enable_tree_felling}，它的 COMMON 家园已删除，本批次改读这里）。
 * 批次 2 才把各工具切换到读这些键 —— 这样只需要一次 toml 迁移。</p>
 */
public class ServerConfig {

    public static final ServerConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    // ===================== 手锯 =====================

    /** 潜行左键原木时是否连锁砍掉整棵树。 */
    public final ModConfigSpec.BooleanValue enableTreeFelling;

    /** 手锯起手（进入切削姿势）时长，tick。 */
    public final ModConfigSpec.IntValue handSawWindupTicks;

    /** 手锯切削（振动）段时长，tick。 */
    public final ModConfigSpec.IntValue handSawVibrateTicks;

    // ===================== 研钵 =====================

    /** 研钵起手阶段时长（tick）：也是服务端开始播音效/粒子的门槛。 */
    public final ModConfigSpec.IntValue mortarWindupTicks;

    /** 研钵捶捣动画的循环周期（tick）。 */
    public final ModConfigSpec.IntValue mortarPoundCycleTicks;

    /** 研钵：单物品加工时长 = 配方 {@code processing_time} × 本系数（<b>不含起手</b>）。 */
    public final ModConfigSpec.DoubleValue mortarDurationMultiplier;

    /** 研钵：配方没写 {@code processing_time}（读到 0）时用的默认加工时长（tick，<b>不含起手</b>）。 */
    public final ModConfigSpec.IntValue mortarDefaultDuration;

    // ===================== 碾钵 =====================

    /** 碾钵起手阶段时长（tick）。 */
    public final ModConfigSpec.IntValue crusherMortarWindupTicks;

    /** 碾钵捶捣动画的循环周期（tick）。 */
    public final ModConfigSpec.IntValue crusherMortarPoundCycleTicks;

    /** 碾钵：单物品加工时长 = 配方 {@code processing_time} × 本系数（<b>不含起手</b>）。 */
    public final ModConfigSpec.DoubleValue crusherMortarDurationMultiplier;

    /** 碾钵：配方没写 {@code processing_time} 时用的默认加工时长（tick，<b>不含起手</b>）。 */
    public final ModConfigSpec.IntValue crusherMortarDefaultDuration;

    // ===================== 搅拌杖 =====================

    /** 搅拌杖起手（探入工作盆并转到搅拌姿势）时长，tick。 */
    public final ModConfigSpec.IntValue stirringStaffWindupTicks;

    /** 搅拌杖加工时长（tick，<b>不含起手</b>）。 */
    public final ModConfigSpec.IntValue stirringStaffProcessingTicks;

    // ===================== 冲压锤 =====================

    /** 冲压锤蓄力门槛：按住至少这么多 tick 才算蓄力完成。 */
    public final ModConfigSpec.IntValue pressHammerChargeTicks;

    /** 冲压锤：松手后多少 tick 内的挥击/命中仍算"蓄力命中"。 */
    public final ModConfigSpec.IntValue pressHammerChargeWindowTicks;

    // ===================== 风箱 =====================

    /** 风箱蓄力门槛：按住至少这么多 tick 才吹出效果。 */
    public final ModConfigSpec.IntValue bellowsChargeTicks;

    /**
     * 风箱触媒类型覆盖（T11 B5）：把某个物品锁定到指定鼓风类型，或直接禁用。
     *
     * <p>格式 {@code "<item_id>=<type_id>"} 或 {@code "<item_id>=none"}，
     * 例如 {@code "minecraft:powder_snow=dndesires:freezing"}。
     * 命中的物品<b>无视优先级</b>只用锁定的类型；{@code none} 表示该物品不再能当介质。
     * 用于解决多模组触媒冲突（CMC 优先级 2000 会压掉 DnD 等同物品类型）。</p>
     */
    public final ModConfigSpec.ConfigValue<List<? extends String>> bellowsTypeOverrides;

    // ===================== 灌注枪 =====================

    /** 灌注枪长按门槛：按住至少这么多 tick 才进入"长按抽取"模式。 */
    public final ModConfigSpec.IntValue infusionGunLongPressThreshold;

    /** 灌注枪抽取节奏：每这么多 tick 抽一次。 */
    public final ModConfigSpec.IntValue infusionGunExtractIntervalTicks;

    static {
        Pair<ServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ServerConfig(ModConfigSpec.Builder builder) {
        // ★ 组名/组注释必须在 push 之前（见类注释）
        builder.comment("Timing and switches of every hand tool.",
                        "Server-authoritative: synced to clients on connect.",
                        "Reconnect required for the change to reach already-connected clients.")
                .translation("create_hand_made.configuration.tools")
                .push("tools");

        // ===================== 手锯 =====================
        builder.comment("Hand Saw.")
                .translation("create_hand_made.configuration.tools.hand_saw")
                .push("hand_saw");
        enableTreeFelling = builder
                .comment("Allow the Hand Saw to fell the entire tree when breaking a log.",
                        "When false, breaking a log breaks only that single block (vanilla behaviour).",
                        "This only affects the chain-felling; cutting recipes and stripping are unaffected.")
                .translation("create_hand_made.configuration.enable_tree_felling")
                .define("enable_tree_felling", true);

        handSawWindupTicks = builder
                .comment("Hand Saw wind-up duration in ticks (moving into the cutting pose).")
                .translation("create_hand_made.configuration.tools.hand_saw.windup_ticks")
                .defineInRange("windup_ticks", 10, 0, 100);

        handSawVibrateTicks = builder
                .comment("Hand Saw cutting (vibration) duration in ticks.",
                        "One item is processed every windup_ticks + vibrate_ticks.")
                .translation("create_hand_made.configuration.tools.hand_saw.vibrate_ticks")
                .defineInRange("vibrate_ticks", 20, 1, 200);
        builder.pop();

        // ===================== 研钵 =====================
        builder.comment("Mortar.")
                .translation("create_hand_made.configuration.tools.mortar")
                .push("mortar");
        mortarWindupTicks = builder
                .comment("Mortar wind-up duration in ticks.",
                        "Also gates when the server starts grinding sounds/particles.",
                        "0 = no wind-up (the tool snaps into position immediately).")
                .translation("create_hand_made.configuration.tools.mortar.windup_ticks")
                .defineInRange("windup_ticks", 10, 0, 100);

        mortarPoundCycleTicks = builder
                .comment("Mortar pounding animation cycle in ticks.",
                        "The server plays the grinding sound once per cycle, exactly when the pestle",
                        "reaches the bottom of the bowl.")
                .translation("create_hand_made.configuration.tools.mortar.pound_cycle_ticks")
                .defineInRange("pound_cycle_ticks", 6, 2, 100);

        mortarDurationMultiplier = builder
                .comment("Mortar: processing time = recipe 'processing_time' x this multiplier.",
                        "The wind-up duration is NOT included (it is added on top).",
                        "Recipes that do not specify 'processing_time' use 'default_duration'.")
                .translation("create_hand_made.configuration.tools.mortar.duration_multiplier")
                .defineInRange("duration_multiplier", 0.2D, 0.05D, 20.0D);

        mortarDefaultDuration = builder
                .comment("Mortar: processing time used when the recipe has no 'processing_time'.",
                        "Does not include the wind-up duration.")
                .translation("create_hand_made.configuration.tools.mortar.default_duration")
                .defineInRange("default_duration", 100, 1, 1200);
        builder.pop();

        // ===================== 碾钵 =====================
        builder.comment("Crusher Mortar.")
                .translation("create_hand_made.configuration.tools.crusher_mortar")
                .push("crusher_mortar");
        crusherMortarWindupTicks = builder
                .comment("Crusher Mortar wind-up duration in ticks.",
                        "Also gates when the server starts grinding sounds/particles.",
                        "0 = no wind-up (the tool snaps into position immediately).")
                .translation("create_hand_made.configuration.tools.crusher_mortar.windup_ticks")
                .defineInRange("windup_ticks", 10, 0, 100);

        crusherMortarPoundCycleTicks = builder
                .comment("Crusher Mortar pounding animation cycle in ticks.",
                        "The server plays the grinding sound once per cycle, exactly when the pestle",
                        "reaches the bottom of the bowl.")
                .translation("create_hand_made.configuration.tools.crusher_mortar.pound_cycle_ticks")
                .defineInRange("pound_cycle_ticks", 6, 2, 100);

        crusherMortarDurationMultiplier = builder
                .comment("Crusher Mortar: processing time = recipe 'processing_time' x this multiplier.",
                        "The wind-up duration is NOT included (it is added on top).",
                        "Recipes that do not specify 'processing_time' use 'default_duration'.")
                .translation("create_hand_made.configuration.tools.crusher_mortar.duration_multiplier")
                .defineInRange("duration_multiplier", 0.1D, 0.05D, 20.0D);

        crusherMortarDefaultDuration = builder
                .comment("Crusher Mortar: processing time used when the recipe has no 'processing_time'.",
                        "Does not include the wind-up duration.")
                .translation("create_hand_made.configuration.tools.crusher_mortar.default_duration")
                .defineInRange("default_duration", 100, 1, 1200);
        builder.pop();

        // ===================== 搅拌杖 =====================
        builder.comment("Stirring Staff.")
                .translation("create_hand_made.configuration.tools.stirring_staff")
                .push("stirring_staff");
        stirringStaffWindupTicks = builder
                .comment("Stirring Staff wind-up duration in ticks.",
                        "Also gates when the server starts stirring sounds/particles.",
                        "0 = no wind-up (the staff snaps into the stirring pose immediately).")
                .translation("create_hand_made.configuration.tools.stirring_staff.windup_ticks")
                .defineInRange("windup_ticks", 8, 0, 100);

        stirringStaffProcessingTicks = builder
                .comment("Stirring Staff processing duration in ticks.",
                        "Does not include the wind-up duration (it is added on top).")
                .translation("create_hand_made.configuration.tools.stirring_staff.processing_ticks")
                .defineInRange("processing_ticks", 52, 1, 1200);
        builder.pop();

        // ===================== 冲压锤 =====================
        builder.comment("Press Hammer.")
                .translation("create_hand_made.configuration.tools.press_hammer")
                .push("press_hammer");
        pressHammerChargeTicks = builder
                .comment("Press Hammer charge time: hold at least this many ticks for a charged press.")
                .translation("create_hand_made.configuration.tools.press_hammer.charge_ticks")
                .defineInRange("charge_ticks", 16, 1, 200);

        pressHammerChargeWindowTicks = builder
                .comment("Press Hammer: for how many ticks after release a swing or a hit still",
                        "counts as a charged hit.")
                .translation("create_hand_made.configuration.tools.press_hammer.charge_window_ticks")
                .defineInRange("charge_window_ticks", 20, 1, 200);
        builder.pop();

        // ===================== 风箱 =====================
        builder.comment("Bellows.")
                .translation("create_hand_made.configuration.tools.bellows")
                .push("bellows");
        bellowsChargeTicks = builder
                .comment("Bellows charge time: hold at least this many ticks to blow.")
                .translation("create_hand_made.configuration.tools.bellows.charge_ticks")
                .defineInRange("charge_ticks", 16, 1, 200);

        bellowsTypeOverrides = builder
                .comment("Force a specific fan type for an item, or disable a type.",
                        "Format: 'item_id=type_id' or 'item_id=none'",
                        "Example: 'minecraft:powder_snow_bucket=create_dragons_plus:freezing'",
                        "Example: 'minecraft:sand=none' (disable entirely)",
                        "'item_id=type_id' is a PREFERENCE: the chosen type wins over getPriority(),",
                        "and other types still serve as a fallback when it cannot process the item.",
                        "Rules whose mod is not installed are skipped silently (debug log only).",
                        "Defaults below only take effect when the matching mod is installed.",
                        "Note: a blaze burner prefers DnD's seething, but DnD also has a dedicated",
                        "medium (dndesires:seething_sail) that needs no configuration at all.",
                        "Only affects the bellows media selection; recipes are untouched.")
                .translation("create_hand_made.configuration.bellows_type_overrides")
                .defineListAllowEmpty("bellows_type_overrides",
                        List.of(
                                // 默认让龙+ 的读取优先级高于更多触媒（"龙+ 优于 CMC"）
                                "minecraft:dragon_head=create_dragons_plus:ending",
                                // 细雪桶：龙+「批量冷冻」优先
                                "minecraft:powder_snow_bucket=create_dragons_plus:freezing",
                                // 沙子：DnD「磨砂」优先，吃不下时仍回落 CMC「扬沙」
                                "minecraft:sand=dndesires:sanding",
                                // 沙类全部对齐到 DnD，避免"沙子走 DnD、红沙走 CMC"的同族不一致
                                "minecraft:red_sand=dndesires:sanding",
                                "minecraft:suspicious_sand=dndesires:sanding"),
                        o -> o instanceof String);
        builder.pop();

        // ===================== 灌注枪 =====================
        builder.comment("Infusion Gun.")
                .translation("create_hand_made.configuration.tools.infusion_gun")
                .push("infusion_gun");
        infusionGunLongPressThreshold = builder
                .comment("Infusion Gun long-press threshold: hold at least this many ticks to enter",
                        "the continuous extraction mode.")
                .translation("create_hand_made.configuration.tools.infusion_gun.long_press_threshold")
                .defineInRange("long_press_threshold", 8, 0, 100);

        infusionGunExtractIntervalTicks = builder
                .comment("Infusion Gun extraction cadence: extract once every this many ticks.")
                .translation("create_hand_made.configuration.tools.infusion_gun.extract_interval_ticks")
                .defineInRange("extract_interval_ticks", 8, 1, 100);
        builder.pop();

        builder.pop();   // tools
    }
}

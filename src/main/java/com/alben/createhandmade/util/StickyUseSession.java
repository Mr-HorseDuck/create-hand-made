package com.alben.createhandmade.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「粘性使用会话」：连续加工时<b>只在第一次长按开始时起手</b>，后续自动续用既不重播起手，
 * 也<b>不再把起手时长算进每一件的加工时间里</b>。
 *
 * <h3>为什么需要（同一根因的两个层次）</h3>
 * <p>连续加工（按住右键一件接一件地磨 / 切 / 搅）时，每件做完客户端会立刻再次 {@code use()} →
 * {@code startUsingItem}，于是 {@code getTicksUsingItem()} 归零。由此产生两个症状：</p>
 * <ol>
 *   <li><b>视觉</b>：起手动画每件都重播一遍（研钵 R4 首次遇到并修掉）；</li>
 *   <li><b>时长 / 相位</b>（<b>b2.6</b> 修）：{@code getUseDuration} 里起手是<b>每次</b>都加的，
 *       续用的每一件都要白白多等一个起手时长；同时加工相位 {@code usedTicks - 起手} 被夹在 0
 *       ⇒ 表现为「1t 加工动画 + 100t 静止」循环。</li>
 * </ol>
 *
 * <h3>做法</h3>
 * <p>两次「正在使用」之间的间隔不超过 {@code stickyTicks} 就视为同一次长按（一个会话）：</p>
 * <ul>
 *   <li>{@link #elapsedTicks}：会话内已经过的 tick（跨自动续用单调递增，不归零）；</li>
 *   <li>{@link #processingElapsedTicks}：再减掉起手 ⇒ <b>加工相位</b>。
 *       音效 / 粒子 / 砧捣动画 / 手锯结算<b>全部共用这一个相位</b>；</li>
 *   <li>{@link #beginUse}：在 {@code use()} 里定下<b>本次使用</b>的总时长 ——
 *       会话首次 = 起手 + 加工，续用 = <b>只有加工</b> ⇒ 起手一个会话只付一次。</li>
 * </ul>
 *
 * <h3>★ 按侧隔离（b2.6 回归修复）</h3>
 * <p><b>故障现象</b>：b2.6 把本类从"只有客户端渲染器用"扩到"客户端与服务端 item 代码都用"之后，
 * 四个工具（手锯 / 研钵 / 碾钵 / 搅拌杖）长按变成在手中原地抽搐、且完全没有加工与音效。</p>
 * <p><b>根因</b>：单人游戏里集成服务端与客户端<b>同一个 JVM、同一份静态表</b>，而
 * {@code ServerPlayer.tickCount} 与 {@code LocalPlayer.tickCount} 是<b>两个不同的时钟</b>
 * （服务端玩家先建号先开始 tick，存在固定时差 {@code skew >= 1}）；两侧又正好<b>共用同一个 UUID</b>，
 * 于是两边互相把对方的会话"重新锚定"：落后一侧的 {@code now < state.lastUsing} 成立 ⇒
 * 重建 {@code State(now)}，领先一侧随后算出的 {@code elapsed} 只能等于 {@code skew}
 * ⇒ {@code elapsed} 被永久钉在 {@code skew < windup} ⇒ {@link #processingElapsedTicks} 恒为 -1
 * ⇒ <b>不结算、不出声、起手走不完（姿势只能在 0 附近抖）</b>。
 * 手锯的 {@code getUseDuration} 是常量 {@code 72000}、与时长缓存无关却也坏，正好证明问题在会话相位。</p>
 * <p><b>修法</b>：状态<b>按侧分开</b>——客户端一套表、服务端一套表，各自只用自己那个
 * {@code tickCount}。侧别由"用哪张表"表达，所以 key 里<b>不再放</b> {@code serverSide}
 * （若哪天合并回一张表，则 key 必须带上侧别）。两张表各只被一条线程访问
 * （单机 = 客户端线程 / 集成服务端线程），{@link ConcurrentHashMap} 只是防御性选择。</p>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>客户端算视觉相位、服务端算结算与音效相位，<b>两端各自维护自己的会话</b>（本就该如此：
 *       两边的 Use 时间轴天然相差一个 ping）。</li>
 *   <li>{@link #beginUse} 的结果必须由 {@link #currentUseDuration} 读回：{@code getUseDuration}
 *       <b>每帧都会被调用</b>，一次使用内返回值恒定，中途跳变会让 Use 计时失步。</li>
 *   <li>两侧独立判断"是否续用" ⇒ 极端情况下（间隔正落 {@code stickyTicks} 边界）可能一侧判"新会话"、
 *       另一侧判"续用"，那一件会多等/少等一个起手时长 —— <b>已接受</b>（不会崩、不会多产出，
 *       产出以服务端 {@code finishUsingItem} 为准）。若实测明显，再做服务端权威同步。</li>
 *   <li>离开会话即清理；只有"会话中途断线"这种极端情况会残留，靠 {@link #pruneIfLarge} 兜底剪枝
 *       （正在被读取的时长条目每次读取都会刷新时间戳，长加工不会被剪掉）。</li>
 * </ul>
 *
 * <h3>调试日志</h3>
 * <p>打开方式二选一：JVM 参数 <b>{@code -Dcreate_hand_made.debugSession=true}</b>，或放一个标记文件
 * <b>{@code <游戏目录>/config/create_hand_made.debug-session}</b>（{@code gradlew runClient} 不用改
 * build.gradle）。默认关闭、关闭时零开销。打开后打印 {@code [CHM-SESSION] NEW/BEGIN/TICK/DUR} 四类行，
 * 每行都带 {@code side=CLIENT|SERVER} 与缩短的 {@code uuid} —— 可直接看到"两侧 UUID 相同、但各自
 * {@code now} 与 {@code sessionStart} 互不干扰"。其中 {@code NEW ... reason=CLOCK_BACKWARDS}
 * 是本次回归的特征行（修好后不应再出现）。</p>
 */
public final class StickyUseSession {

    /** 统一的容差（tick）：研钵 R4 实测值，四个工具共用（原版右键连点间隔 4t，留 1t 余量）。 */
    public static final int DEFAULT_STICKY_TICKS = 5;

    /**
     * 调试开关：二选一 ——
     * <ol>
     *   <li>JVM 参数 {@code -Dcreate_hand_made.debugSession=true}（IDE 运行配置最方便）；</li>
     *   <li>标记文件 <b>{@code <工作目录>/config/create_hand_made.debug-session}</b> 存在即可 ——
     *       {@code gradlew runClient} 的工作目录就是 {@code run/}，所以放
     *       {@code run/config/create_hand_made.debug-session}（无需改 build.gradle）。</li>
     * </ol>
     * 类初始化时读一次（运行中新建标记文件要重启才生效）。关闭时零开销。
     */
    private static final boolean DEBUG = System.getProperty("create_hand_made.debugSession") != null
            ? Boolean.parseBoolean(System.getProperty("create_hand_made.debugSession"))
            : debugMarkerExists();

    private static boolean debugMarkerExists() {
        try {
            // 相对当前工作目录 —— gradle runClient / runServer 的工作目录就是 run/，
            // 所以标记文件放在 run/config/ 下即可。不用 FMLPaths：类初始化时少依赖一个
            // 尚未就绪的加载器状态，任何异常都吞掉（拿不到就当关闭）。
            return java.nio.file.Files.exists(
                    java.nio.file.Path.of("config", "create_hand_made.debug-session"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 会话 key：玩家 + 物品 + 手。侧别由"用哪张表"表达，故不在这里。 */
    private record Key(UUID player, Item item, InteractionHand hand) {}

    /** 「本次使用总时长」的 key：玩家 + 物品（同一 tick 一名玩家只可能在使用一件物品）。 */
    private record UseKey(UUID player, Item item) {}

    private static final class State {
        private final int sessionStart;
        private int lastUsing;

        private State(int now) {
            this.sessionStart = now;
            this.lastUsing = now;
        }
    }

    private static final class UseDuration {
        private final int duration;
        private int readAt;

        private UseDuration(int duration, int readAt) {
            this.duration = duration;
            this.readAt = readAt;
        }
    }

    // ================= ★ 按侧隔离的四张表 =================
    // 单机里客户端与服务端同 JVM、玩家 UUID 也相同，唯一能把两侧彻底分开的做法就是"不共用表"。
    // 各张表在单机下分别只被一条线程访问（客户端线程 / 集成服务端线程）。

    private static final Map<Key, State> CLIENT_STATES = new ConcurrentHashMap<>();
    private static final Map<Key, State> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UseKey, UseDuration> CLIENT_DURATIONS = new ConcurrentHashMap<>();
    private static final Map<UseKey, UseDuration> SERVER_DURATIONS = new ConcurrentHashMap<>();

    private StickyUseSession() {}

    /** 侧别判定：与服务端/客户端 item 代码里惯用的 {@code level.isClientSide} 同源。 */
    private static boolean serverSide(Player player) {
        return !player.level().isClientSide();
    }

    private static Map<Key, State> states(Player player) {
        return serverSide(player) ? SERVER_STATES : CLIENT_STATES;
    }

    private static Map<UseKey, UseDuration> durations(Player player) {
        return serverSide(player) ? SERVER_DURATIONS : CLIENT_DURATIONS;
    }

    // ================= 会话计时 =================

    /**
     * 取「本会话内已经过的 tick」（含起手那一段，单调递增）。
     *
     * @return {@code >= 0}：在会话内（含松手后 {@code stickyTicks} tick 的"粘性尾巴"）；
     *         {@code -1}：不在会话里（调用方应显示"未起手"状态）
     */
    public static int elapsedTicks(Player player, ItemStack stack, InteractionHand hand, int stickyTicks) {
        if (player == null || stack.isEmpty()) return -1;

        int now = player.tickCount;
        Key key = new Key(player.getUUID(), stack.getItem(), hand);
        Map<Key, State> states = states(player);

        // 与各渲染器一致的"正在使用"判定：手别匹配 + 物品与组件匹配（用副本传入也能判定成功）
        boolean using = player.isUsingItem()
                && player.getUsedItemHand() == hand
                && ItemStack.isSameItemSameComponents(player.getUseItem(), stack);

        State state = states.get(key);
        if (using) {
            if (state == null || now < state.lastUsing || now - state.lastUsing > stickyTicks) {
                // ★ 这里就是 b2.6 回归的开关：两侧共表时，落后时钟每次调用都会落到这个分支
                debug("NEW   side=%s uuid=%s item=%s hand=%s now=%d reason=%s",
                        sideName(player), shortId(player), itemId(stack), hand, now,
                        resetReason(state, now, stickyTicks));
                state = new State(now);   // 新会话：起手从头来
                states.put(key, state);
            } else {
                state.lastUsing = now;
            }
            pruneIfLarge(states, durations(player), now, stickyTicks);
            return Math.max(0, now - state.sessionStart);
        }

        // 不在使用：只要还在粘性尾巴内，就继续保持"已起手"状态（避免每件之间闪回未起手）
        if (state != null && now >= state.lastUsing && now - state.lastUsing <= stickyTicks) {
            return Math.max(0, now - state.sessionStart);
        }
        states.remove(key);
        return -1;
    }

    /**
     * 取「本会话内<b>加工阶段</b>已经过的 tick」= 会话 tick − 起手时长。
     *
     * <p>这是 b2.6 之后所有"周期 / 相位"的唯一锚点：会话首次使用时它与旧的
     * {@code usedTicks - 起手} 完全等价（第一件的行为不变），续用时它继续累加，
     * 于是每件之间不再出现"起手那么长的静止"。</p>
     *
     * <p>两端都会调用（客户端渲染器 + 服务端 {@code onUseTick}），各自读自己那侧的表。</p>
     *
     * @return {@code >= 0}：已经进入加工阶段；{@code -1}：不在会话内，或还在起手期内
     */
    public static int processingElapsedTicks(Player player, ItemStack stack, InteractionHand hand,
                                             int stickyTicks, int windupTicks) {
        int elapsed = elapsedTicks(player, stack, hand, stickyTicks);
        if (elapsed < 0) return -1;
        int processing = elapsed - Math.max(0, windupTicks);
        if (processing < 0) return -1;

        // 每 20t 采样一行：两侧的 elapsed 应当各自单调增长（互不重锚）
        if (DEBUG && processing > 0 && processing % 20 == 0) {
            debug("TICK  side=%s uuid=%s item=%s now=%d start=%d elapsed=%d processing=%d",
                    sideName(player), shortId(player), itemId(stack),
                    player.tickCount, player.tickCount - elapsed, elapsed, processing);
        }
        return processing;
    }

    // ================= 「本次使用」的总时长 =================

    /**
     * 在 {@code use()} 里（{@code player.startUsingItem(hand)} <b>之前</b>）定下本次使用的总时长。
     *
     * <p>会话首次使用 = {@code windup + processing}；同一次长按里的后续自动续用 = {@code processing}
     * —— 起手一个会话只付一次。</p>
     *
     * @param stickyTicks     会话容差，一般传 {@link #DEFAULT_STICKY_TICKS}
     * @param windupTicks     起手时长（可能为 0 = 不要起手；只在会话首次计入总时长）
     * @param processingTicks 单件加工时长（&lt; 1 时按 1 处理）
     * @return 本次使用的总时长（tick，恒 {@code >= 1}）
     */
    public static int beginUse(Player player, ItemStack stack, InteractionHand hand, int stickyTicks,
                               int windupTicks, int processingTicks) {
        int processing = Math.max(1, processingTicks);
        int windup = Math.max(0, windupTicks);
        if (player == null || stack.isEmpty()) return windup + processing;   // 兜底：按"首次使用"算

        int now = player.tickCount;
        Key key = new Key(player.getUUID(), stack.getItem(), hand);
        Map<Key, State> states = states(player);
        State state = states.get(key);

        // 上一次使用刚刚结束（在粘性容差内）⇒ 同一次长按的续用：起手不再计入
        boolean continuation = state != null && now >= state.lastUsing && now - state.lastUsing <= stickyTicks;
        if (continuation) {
            state.lastUsing = now;
        } else {
            state = new State(now);
            states.put(key, state);
        }

        int duration = (continuation ? 0 : windup) + processing;
        durations(player).put(new UseKey(player.getUUID(), stack.getItem()), new UseDuration(duration, now));
        pruneIfLarge(states, durations(player), now, stickyTicks);

        // 新会话必打；续用每 20t 采样一行，避免 P 很小时刷屏
        if (DEBUG && (!continuation || now % 20 == 0)) {
            debug("BEGIN side=%s uuid=%s item=%s hand=%s now=%d cont=%s duration=%d (W=%d P=%d)",
                    sideName(player), shortId(player), itemId(stack), hand, now, continuation, duration, windup, processing);
        }
        return duration;
    }

    /**
     * 读回 {@link #beginUse} 定下的「本次使用总时长」。
     *
     * <p>供 {@code getUseDuration(stack, entity)} 使用 —— 该方法<b>每帧都会被调用</b>，
     * 所以这里必须是纯读取、一次使用内恒定。读不到（旧存档 / 会话状态被剪枝等极端路径）时
     * 返回 {@code fallback}（调用方一般传"首次使用"的语义 = {@code 起手 + 加工}，最保守）。</p>
     */
    public static int currentUseDuration(Player player, ItemStack stack, int fallback) {
        if (player == null || stack.isEmpty()) return fallback;
        UseDuration decided = durations(player).get(new UseKey(player.getUUID(), stack.getItem()));
        if (decided == null) {
            if (DEBUG && player.tickCount % 20 == 0) {
                debug("DUR   side=%s uuid=%s item=%s now=%d duration=%d (FALLBACK: 没有缓存)",
                        sideName(player), shortId(player), itemId(stack), player.tickCount, fallback);
            }
            return fallback;
        }
        // 正在被读取 ⇒ 说明这次使用还活着，刷新时间戳，长加工（最长 1200t）不会被剪掉
        decided.readAt = player.tickCount;
        if (DEBUG && player.tickCount % 20 == 0) {
            debug("DUR   side=%s uuid=%s item=%s now=%d duration=%d (memo)",
                    sideName(player), shortId(player), itemId(stack), player.tickCount, decided.duration);
        }
        return decided.duration;
    }

    /** 兜底剪枝：会话中途断线等极端情况会留下残留条目，表变大时丢掉明显过期的。每侧各剪各的。 */
    private static void pruneIfLarge(Map<Key, State> states, Map<UseKey, UseDuration> durations,
                                     int now, int stickyTicks) {
        // 会话条目：过期即可丢
        if (states.size() > 64) {
            int cutoff = now - Math.max(100, stickyTicks * 4);
            states.entrySet().removeIf(entry -> entry.getValue().lastUsing < cutoff);
        }
        // "本次使用总时长"条目：单次使用最长可到 MAX_PROCESSING_TICKS(1200) + 起手，
        // 而服务端在一次使用中不一定会再读 getUseDuration()（客户端渲染器每帧读、会刷新时间戳），
        // 所以这里的过期阈值必须远大于"一次使用可能持续多久"，否则长加工中途被剪掉会让
        // getUseDuration 突然退回兜底值 ⇒ Use 计时失步。
        if (durations.size() > 64) {
            int cutoff = now - 2400;
            durations.entrySet().removeIf(entry -> entry.getValue().readAt < cutoff);
        }
    }

    // ================= 调试（-Dcreate_hand_made.debugSession=true） =================

    /** 重建会话的原因 —— {@code CLOCK_BACKWARDS} 就是本次回归的特征（修好后不应再出现）。 */
    private static String resetReason(State previous, int now, int stickyTicks) {
        if (previous == null) return "NO_STATE";
        if (now < previous.lastUsing) {
            return "CLOCK_BACKWARDS(prevLast=" + previous.lastUsing + ",delta=" + (now - previous.lastUsing) + ")";
        }
        return "EXPIRED(delta=" + (now - previous.lastUsing) + ",sticky=" + stickyTicks + ")";
    }

    private static String sideName(Player player) {
        return serverSide(player) ? "SERVER" : "CLIENT";
    }

    private static String shortId(Player player) {
        return player.getUUID().toString().substring(0, 8);
    }

    private static String itemId(ItemStack stack) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void debug(String format, Object... args) {
        if (!DEBUG) return;
        System.out.println("[CHM-SESSION] " + String.format(Locale.ROOT, format, args));
    }
}

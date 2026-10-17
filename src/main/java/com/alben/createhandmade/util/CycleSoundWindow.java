package com.alben.createhandmade.util;

/**
 * 「本件加工窗口内是否真的响过周期音」的判定 —— 用于 T10 b2.7c 的「完成音保底」。
 *
 * <h3>为什么需要</h3>
 * <p>加工时长（手锯 {@code vibrate_ticks} / 研钵碾钵 {@code processing_time × 系数} / 搅拌杖
 * {@code processing_ticks}）可以被配得很短，短到"一个物品的加工窗口里一次周期音都落不进来"
 * （周期音门限是 {@code t % 4 == 0} 或 {@code t % cycle == slamTick(cycle)}）——
 * 那样每件加工完都是静音的。</p>
 * <p>b2.7 的做法是"完成时<b>无条件</b>补一声"，实测后确认要改成<b>保底</b>：
 * <b>本件没响过周期音才补</b>；同时周期音在结算/完成 tick <b>让位</b>（不播），由完成音接管那一 tick。
 * 两者配合 = "每件至少一声" + "不会同 tick 双响"。</p>
 *
 * <h3>为什么用"窗口判定"而不是计数器</h3>
 * <p>这是<b>无状态</b>方案。完成那一刻我们已知两个量：</p>
 * <ul>
 *   <li>{@code phase} = 本件最后一个 tick 的会话加工相位
 *       （{@link StickyUseSession#processingElapsedTicks}）；</li>
 *   <li>{@code duration} = 本次使用的总时长（{@code getUseDuration}，b2.6 起由
 *       {@code StickyUseSession.beginUse} 定值，含会话首次的起手）。</li>
 * </ul>
 * <p>相位在<b>一次使用内每 tick 恰好 +1</b>，所以本件的窗口就是
 * {@code [phase - (duration - 1), phase]}（含两端，正好 {@code duration} 个 tick）——
 * 不需要任何每玩家状态，也不会因为哪次漏了重置而"保底永不触发"。</p>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>{@code lo} 会被夹到 {@code >= 0}（起手期内相位是负的，那时周期音本来就响不了）；</li>
 *   <li>调用方要把<b>被让位掉的完成/结算 tick</b> 排除在 {@code hi} 之外
 *       （研钵/碾钵/搅拌杖传 {@code phase - 1}，手锯传结算 tick 的前一个 tick）；</li>
 *   <li>窗口为空（{@code hi < lo}）⇒ 返回 {@code false} ⇒ 本件肯定没响过 ⇒ <b>该保底</b>。</li>
 * </ul>
 */
public final class CycleSoundWindow {

    private CycleSoundWindow() {}

    /**
     * 本件窗口 {@code [lo, hi]}（会话加工相位，含两端）内是否存在满足
     * {@code t % period == offset} 的 tick。
     *
     * @param lo     窗口起点（会被夹到 {@code >= 0}）
     * @param hi     窗口终点（已排除被"让位"的完成/结算 tick）
     * @param period 周期音的周期（手锯 / 搅拌杖 = 4；研钵碾钵 = {@code pound_cycle_ticks}）
     * @param offset 周期音在周期内的相位（手锯 / 搅拌杖 = 0；研钵碾钵 = {@code slamTick(cycle)}）
     * @return {@code true} = 本件响过周期音（不需要保底）
     */
    public static boolean hasCycleTick(int lo, int hi, int period, int offset) {
        if (period <= 0) return false;
        int first = Math.max(0, lo);
        if (first > hi) return false;
        // 从 first 起第一个 ≡ offset (mod period) 的 tick
        first += Math.floorMod(offset - first, period);
        return first <= hi;
    }
}

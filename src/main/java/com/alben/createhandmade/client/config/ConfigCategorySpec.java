package com.alben.createhandmade.client.config;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 配置界面里的「一页」：左侧分类栏里的一项 + 右侧要显示的选项。
 *
 * <p>一页最多放几项？界面按固定行高（{@link HandMadeConfigScreen#ROW_H}）自上而下排，
 * 不滚动——所以分类要拆得足够细（主配置每类 ≤3 项；女仆配置 12 项拆成 3 页）。
 * 这样省掉一整套自绘滚动列表的逻辑，也没有「滚到一半控件错位」的风险。</p>
 */
public final class ConfigCategorySpec {

    public final Component title;
    public final List<ConfigOption> options;

    /**
     * 这一页的值是不是<b>服务端权威</b>（{@code ModConfig.Type.SERVER}）。
     *
     * <p>只读判定要跟着它走，而不是整页一刀切：{@code COMMON} 类型的本地配置在多人游戏里
     * <b>改的是自己机器上的文件</b>，不该锁。目前所有页都是 {@code SERVER}（女仆那份本地
     * `blocked_mods` 已改为写死在代码里，不再开放配置），这套判定留着给将来的本地配置用。</p>
     */
    public final boolean serverAuthoritative;

    /** 服务端权威的页（主配置全部、女仆的 SERVER 项）。 */
    public ConfigCategorySpec(Component title, List<ConfigOption> options) {
        this(title, options, true);
    }

    public ConfigCategorySpec(Component title, List<ConfigOption> options, boolean serverAuthoritative) {
        this.title = title;
        this.options = List.copyOf(options);
        this.serverAuthoritative = serverAuthoritative;
    }

    /** 语言键简写。 */
    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }
}

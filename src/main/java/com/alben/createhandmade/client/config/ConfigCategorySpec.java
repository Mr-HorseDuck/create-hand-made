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

    public ConfigCategorySpec(Component title, List<ConfigOption> options) {
        this.title = title;
        this.options = List.copyOf(options);
    }

    /** 语言键简写。 */
    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }
}

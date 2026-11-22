package com.alben.createhandmade.client.config;

import java.util.List;

/**
 * 「别处提供的配置页」——本模组主代码用它向配置界面交出额外的分类，
 * 而不需要引用提供方的任何类。
 *
 * <p>目前唯一的实现是 tlmCompat 里的女仆配置
 * （{@code com.colin101.maidfactory.client.MaidConfigSectionProvider}）：
 * 主代码只 {@code Class.forName} 拿到实例、转成这个接口，界面就照常渲染。</p>
 *
 * <p>为什么不是「主代码直接读女仆配置」：{@code src/tlmCompat} 依赖方向是
 * <b>tlmCompat → main</b>，main 看不到 {@code com.colin101.maidfactory.*}，
 * 女仆配置类也只有装了 TLM 才存在。</p>
 */
public interface ConfigSectionProvider {

    /** 要追加到界面里的分类（按顺序排在主配置后面）。 */
    List<ConfigCategorySpec> categories();

    /** 落盘：把本提供方的配置 SPEC 写回 toml。 */
    void save();
}

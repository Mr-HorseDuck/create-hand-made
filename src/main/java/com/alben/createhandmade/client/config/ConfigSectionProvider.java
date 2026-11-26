package com.alben.createhandmade.client.config;

import java.util.List;

/**
 * 「别处提供的配置页」——本模组主代码用它向配置界面交出额外的分类，
 * 而不需要引用提供方的任何类。
 *
 * <p>目前唯一的实现是独立模组 maid-factory 里的女仆配置
 * （{@code com.colin101.maidfactory.client.MaidConfigSectionProvider}）：
 * 主代码只 {@code Class.forName} 拿到实例、转成这个接口，界面就照常渲染。</p>
 *
 * <p>为什么不是「主代码直接读女仆配置」：依赖方向是 <b>maid-factory → 主模组</b>，
 * 主模组看不到 {@code com.colin101.maidfactory.*}，那些类也只有装了 maid-factory 才存在。</p>
 */
public interface ConfigSectionProvider {

    /** 要追加到界面里的分类（按顺序排在主配置后面）。 */
    List<ConfigCategorySpec> categories();

    /**
     * 落盘：把本提供方的配置 SPEC 写回 toml。
     *
     * @param serverConfigsLoaded 当前是否处在「服务端配置已加载」的环境（单人存档里为 true）。
     *                            提供方只在 true 时保存 {@code SERVER} 类型的 SPEC；
     *                            {@code COMMON} 类型的本地配置任何时候都可以存。
     */
    void save(boolean serverConfigsLoaded);
}

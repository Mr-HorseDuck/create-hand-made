package com.colin101.maidfactory;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;

/**
 * 女仆兼容专用调试日志。
 * ★ 只读 main 的 Config.debugEnabled，不修改 main 任何类。
 */
public final class MaidDebug {

    private MaidDebug() {}

    public static void log(String format, Object... args) {
        // ★ Config 是 SERVER 配置：女仆初始化阶段（addMaidTask）它可能还没加载，
        //   此时直接 get() 在开发环境会抛 IllegalStateException，所以先看 isLoaded()。
        if (!Config.SPEC.isLoaded()) return;
        if (Config.INSTANCE.debugEnabled.get()) {
            CreateHandMade.LOGGER.info("[HandMade-Debug] " + format, args);
        }
    }
}
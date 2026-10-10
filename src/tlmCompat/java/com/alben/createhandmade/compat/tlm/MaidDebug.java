package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;

/**
 * 女仆兼容专用调试日志。
 * ★ 只读 main 的 Config.debugEnabled，不修改 main 任何类。
 */
public final class MaidDebug {

    private MaidDebug() {}

    public static void log(String format, Object... args) {
        if (Config.INSTANCE.debugEnabled.get()) {
            CreateHandMade.LOGGER.info("[HandMade-Debug] " + format, args);
        }
    }
}
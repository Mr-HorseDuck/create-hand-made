package com.alben.createhandmade.util;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * 调试用：打印 PoseStack 当前矩阵。
 * 仅在 Config.debugEnabled = true 时输出，最多打 20 次避免刷屏。
 */
public final class PoseDebug {

    private static final int MAX_PRINTS = 20;
    private static int counter = 0;

    private PoseDebug() {}

    public static void print(String label, PoseStack ms) {
        // ★ 受 Config.debugEnabled 控制
        if (!Config.INSTANCE.debugEnabled.get()) return;
        if (counter >= MAX_PRINTS) return;
        counter++;

        Matrix4f m = ms.last().pose();
        CreateHandMade.LOGGER.info("[HandMade-Pose] {} #{}\n{}", label, counter, format(m));
    }

    private static String format(Matrix4f m) {
        return String.format(
                "  [ % .4f  % .4f  % .4f  % .4f ]\n" +
                "  [ % .4f  % .4f  % .4f  % .4f ]\n" +
                "  [ % .4f  % .4f  % .4f  % .4f ]\n" +
                "  [ % .4f  % .4f  % .4f  % .4f ]",
                m.m00(), m.m10(), m.m20(), m.m30(),
                m.m01(), m.m11(), m.m21(), m.m31(),
                m.m02(), m.m12(), m.m22(), m.m32(),
                m.m03(), m.m13(), m.m23(), m.m33());
    }
}
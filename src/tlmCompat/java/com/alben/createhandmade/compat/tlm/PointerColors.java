package com.alben.createhandmade.compat.tlm;

/**
 * 指杆标记的颜色键。
 * ★ 属于 tlmCompat，main 侧不再感知这些语义。
 *   值必须与 main 的 HighlightBlockPacket 保持一致的 int 编码（0xRRGGBB，无 alpha）。
 */
public final class PointerColors {

    private PointerColors() {}

    /** 工作方块（MODE_MARK） */
    public static final int WORK = 0xff00;

    /** 输入（MODE_MARK / MODE_LIQUID 共用） */
    public static final int INPUT = 0x80ff;

    /** 输出（MODE_MARK / MODE_LIQUID 共用） */
    public static final int OUTPUT = 0x0080ff;

    /** ★ 过剩输出（MODE_LIQUID 专用，独立颜色键避免覆盖 OUTPUT） */
    public static final int OVERFLOW = 0xff8000;
}
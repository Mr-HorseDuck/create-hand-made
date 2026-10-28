package com.alben.createhandmade.recipe;

import javax.annotation.Nullable;

/**
 * 手搓工具类型（用于三层配方系统）。
 *
 * 注意：冲压锤有两条独立的配方路径，需要拆成两个 id：
 *   - PRESS_HAMMER_BASIN：工作盆（COMPACTING + 4/9 合 1）
 *   - PRESS_HAMMER_DEPOT：置物台/传送带（PRESSING）
 * 两者共享同一个物品（冲压锤），但 L2 过滤和 L3 归属需要独立控制。
 */
public enum ToolType {
    HAND_SAW("hand_saw"),
    /** ★ 独立 L2 过滤层：手锯的切石候选，与锯木分开 */
    HAND_SAW_STONECUTTING("hand_saw_stonecutting"),
    MORTAR("mortar"),
    CRUSHER_MORTAR("crusher_mortar"),
    STIRRING_STAFF("stirring_staff"),
    /** 冲压锤 · 工作盆路径 */
    PRESS_HAMMER_BASIN("press_hammer_basin"),
    /** 冲压锤 · 置物台/传送带路径 */
    PRESS_HAMMER_DEPOT("press_hammer_depot"),
    INFUSION_GUN("infusion_gun"),
    POINTER("pointer"),
    BELLOWS("bellows");

    public final String id;

    ToolType(String id) {
        this.id = id;
    }

    @Nullable
    public static ToolType byId(String id) {
        for (ToolType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
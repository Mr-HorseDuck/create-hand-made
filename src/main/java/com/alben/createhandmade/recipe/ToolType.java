package com.alben.createhandmade.recipe;

import javax.annotation.Nullable;

/**
 * 手搓工具类型（用于三层配方系统）
 */
public enum ToolType {
    HAND_SAW("hand_saw"),
    /** ★ 独立 L2 过滤层：手锯的切石候选，与锯木分开 */
    HAND_SAW_STONECUTTING("hand_saw_stonecutting"),
    MORTAR("mortar"),
    CRUSHER_MORTAR("crusher_mortar"),
    STIRRING_STAFF("stirring_staff"),
    PRESS_HAMMER("press_hammer"),
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
package com.alben.createhandmade.recipe;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 风箱独占配方（{@code create_hand_made:bellows_recipe}）的 {@code fan_type} 取值。
 *
 * <p>四种鼓风与 Create 的四个 {@code FanProcessingType} 一一对应，但这里<b>刻意不引用</b>
 * Create 的类型对象（{@code AllFanProcessingTypes.BlastingType} 等）：配方层只需要
 * "是哪一种鼓风"这一条信息，用本模组自己的枚举可以让 {@code recipe} 包完全不依赖
 * Create 的风扇实现类。与 Create 类型的互相映射放在使用方
 * （{@code BellowsItem} 里"副手介质 → 鼓风类型"的那 4 选 1 逻辑）—— 见 T6 批次 2。</p>
 *
 * <p>常量名与 Create 的 4 个类型 id 的后缀一致：{@code create:blasting} → {@link #BLASTING}。</p>
 */
public enum FanType {

    /** 鼓风熔炼（Create {@code create:blasting}，底层 vanilla {@code RecipeType.SMELTING} / {@code RecipeType.BLASTING}）。 */
    BLASTING,

    /** 鼓风烟熏（Create {@code create:smoking}，底层 vanilla {@code RecipeType.SMOKING}）。 */
    SMOKING,

    /** 鼓风缠魂（Create {@code create:haunting}，底层 {@code AllRecipeTypes.HAUNTING}）。 */
    HAUNTING,

    /** 鼓风洗涤（Create {@code create:splashing}，底层 {@code AllRecipeTypes.SPLASHING}）。 */
    SPLASHING;

    /** JSON / KubeJS 里写的字符串形式（{@code blasting} / {@code smoking} / …）。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 解析 {@code fan_type} 字符串；未知取值返回 null（由调用方负责报错）。 */
    @Nullable
    public static FanType byId(String id) {
        for (FanType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** 错误信息里列出的合法取值；从枚举推导，新增常量时不会漏改文案。 */
    public static String validIds() {
        StringBuilder sb = new StringBuilder();
        for (FanType type : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type.id());
        }
        return sb.toString();
    }
}

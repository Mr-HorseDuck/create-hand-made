package com.alben.createhandmade.client.config;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 配置界面里的「一项设置」—— <b>纯数据描述，不含任何绘制代码</b>。
 *
 * <p>本模组 0.7.0 起不再依赖 Cloth Config：界面自己画（见 {@link HandMadeConfigScreen}），
 * 但「有哪些可调项、怎么读写」与界面解耦——主配置在这里直接读 {@code Config.INSTANCE} 的
 * {@code ForgeConfigSpec.Value}，女仆配置则由 tlmCompat 侧实现
 * {@link ConfigSectionProvider} 提供同样的描述对象。这样：
 * <ul>
 *   <li>主代码不需要引用女仆配置类（tlmCompat → main 是单向依赖）；</li>
 *   <li>换个界面实现（比如以后想做成 Create 皮肤）不用动配置声明。</li>
 * </ul>
 *
 * <p>读写都走 {@code Supplier} / {@code Consumer}，不做「先改界面再批量提交」的暂存——
 * 改一下就写进 {@code ForgeConfigSpec} 的值里，关界面时统一落盘 + 广播同步。
 * 与以前 Cloth 的 {@code setSaveConsumer} 语义一致。</p>
 */
public abstract class ConfigOption {

    /** 显示名（语言键）。 */
    public final Component label;

    /** 悬停说明，可多行；空列表表示不显示。 */
    public final List<Component> tooltip;

    protected ConfigOption(Component label, List<Component> tooltip) {
        this.label = label;
        this.tooltip = List.copyOf(tooltip);
    }

    /** 恢复默认值（「恢复本页默认」按钮用）。 */
    public abstract void resetToDefault();

    /** 默认值的可读形式——悬停说明里显示「默认：X」。 */
    public abstract Component defaultText();

    /** 当前值是否已经等于默认值（不等的项会在界面上打一个点）。 */
    public abstract boolean isDefault();

    /**
     * 当前值的快照，配合 {@link #restore(Object)} 实现「恢复本页默认」之后的<b>撤销</b>。
     *
     * <p>用 {@code Object} 而不是泛型，是因为一页里混着布尔和整数，
     * 界面侧只把它们原样存进 {@code Map<ConfigOption, Object>} 再原样放回去。</p>
     */
    public abstract Object snapshot();

    /** 把 {@link #snapshot()} 拿到的值写回去；类型不符时静默忽略。 */
    public abstract void restore(Object value);

    // ==================== 布尔开关 ====================

    /** 画成一个「开 / 关」按钮。 */
    public static final class Bool extends ConfigOption {

        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;
        private final boolean defaultValue;

        public Bool(Component label, List<Component> tooltip, boolean defaultValue,
                    Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(label, tooltip);
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.setter = setter;
        }

        public boolean get() {
            return getter.get();
        }

        public void set(boolean value) {
            setter.accept(value);
        }

        public boolean defaultValue() {
            return defaultValue;
        }

        @Override
        public Component defaultText() {
            return Component.translatable(defaultValue ? "options.on" : "options.off");
        }

        @Override
        public boolean isDefault() {
            return get() == defaultValue;
        }

        @Override
        public Object snapshot() {
            return get();
        }

        @Override
        public void restore(Object value) {
            if (value instanceof Boolean bool) {
                set(bool);
            }
        }

        @Override
        public void resetToDefault() {
            set(defaultValue);
        }
    }

    // ==================== 整数（滑块） ====================

    /**
     * 画成一个整数滑块。
     *
     * <p>范围来自 Forge 的 {@code defineInRange}，所以界面里永远不可能调出非法值；
     * 这里仍然 {@code clamp} 一次，防止配置文件被手改成越界值后滑块位置算崩。</p>
     */
    public static final class Int extends ConfigOption {

        private final int min;
        private final int max;
        private final int defaultValue;
        private final Supplier<Integer> getter;
        private final IntConsumer setter;

        public Int(Component label, List<Component> tooltip,
                   int min, int max, int defaultValue,
                   Supplier<Integer> getter, IntConsumer setter) {
            super(label, tooltip);
            this.min = min;
            this.max = Math.max(max, min + 1);
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.setter = setter;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public int get() {
            int raw = getter.get();
            return Math.max(min, Math.min(max, raw));
        }

        public void set(int value) {
            setter.accept(Math.max(min, Math.min(max, value)));
        }

        public int defaultValue() {
            return defaultValue;
        }

        @Override
        public Component defaultText() {
            return Component.literal(Integer.toString(defaultValue));
        }

        @Override
        public boolean isDefault() {
            return get() == defaultValue;
        }

        @Override
        public Object snapshot() {
            return get();
        }

        @Override
        public void restore(Object value) {
            if (value instanceof Integer number) {
                set(number);
            }
        }

        @Override
        public void resetToDefault() {
            set(defaultValue);
        }
    }
}

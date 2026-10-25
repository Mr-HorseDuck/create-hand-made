package com.alben.createhandmade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端权威配置：所有工具的时间参数与开关。
 *
 * <p>Fabric 没有内置的 SERVER 配置同步，因此这里自己实现：</p>
 * <ul>
 *   <li>服务端把 {@code config/create_hand_made-server.json} 作为权威值；</li>
 *   <li>玩家加入时通过 {@code ServerConfigPayload} 把整份 JSON 推给客户端，
 *       客户端直接覆盖内存里的值（不落盘），从而保证 Use 计时两端一致。</li>
 * </ul>
 */
public final class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * ★ 必须在 {@link #INSTANCE} 之前初始化：{@code INSTANCE} 的实例字段在构造时会
     * 往这里登记配置项，若本字段后声明则此时仍为 null。
     */
    private static final List<Value<?>> ALL_VALUES = new ArrayList<>();

    public static final ServerConfig INSTANCE = new ServerConfig();

    // ===================== 手锯 =====================
    public final BoolValue enableTreeFelling = new BoolValue("tools.hand_saw.enable_tree_felling", true);
    public final IntValue handSawWindupTicks = new IntValue("tools.hand_saw.windup_ticks", 10, 0, 100);
    public final IntValue handSawVibrateTicks = new IntValue("tools.hand_saw.vibrate_ticks", 20, 1, 200);

    // ===================== 研钵 =====================
    public final IntValue mortarWindupTicks = new IntValue("tools.mortar.windup_ticks", 10, 0, 100);
    public final IntValue mortarPoundCycleTicks = new IntValue("tools.mortar.pound_cycle_ticks", 6, 2, 100);
    public final DoubleValue mortarDurationMultiplier =
            new DoubleValue("tools.mortar.duration_multiplier", 0.2D, 0.05D, 20.0D);
    public final IntValue mortarDefaultDuration = new IntValue("tools.mortar.default_duration", 100, 1, 1200);

    // ===================== 碾钵 =====================
    public final IntValue crusherMortarWindupTicks = new IntValue("tools.crusher_mortar.windup_ticks", 10, 0, 100);
    public final IntValue crusherMortarPoundCycleTicks =
            new IntValue("tools.crusher_mortar.pound_cycle_ticks", 6, 2, 100);
    public final DoubleValue crusherMortarDurationMultiplier =
            new DoubleValue("tools.crusher_mortar.duration_multiplier", 0.1D, 0.05D, 20.0D);
    public final IntValue crusherMortarDefaultDuration =
            new IntValue("tools.crusher_mortar.default_duration", 100, 1, 1200);

    // ===================== 搅拌杖 =====================
    public final IntValue stirringStaffWindupTicks = new IntValue("tools.stirring_staff.windup_ticks", 8, 0, 100);
    public final IntValue stirringStaffProcessingTicks =
            new IntValue("tools.stirring_staff.processing_ticks", 52, 1, 1200);

    // ===================== 冲压锤 =====================
    public final IntValue pressHammerChargeTicks = new IntValue("tools.press_hammer.charge_ticks", 16, 1, 200);
    public final IntValue pressHammerChargeWindowTicks =
            new IntValue("tools.press_hammer.charge_window_ticks", 20, 1, 200);

    // ===================== 风箱 =====================
    public final IntValue bellowsChargeTicks = new IntValue("tools.bellows.charge_ticks", 16, 1, 200);

    // ===================== 灌注枪 =====================
    public final IntValue infusionGunLongPressThreshold =
            new IntValue("tools.infusion_gun.long_press_threshold", 8, 0, 100);
    public final IntValue infusionGunExtractIntervalTicks =
            new IntValue("tools.infusion_gun.extract_interval_ticks", 8, 1, 100);

    private ServerConfig() {
        // 字段初始化时已把各值登记进 ALL_VALUES
    }

    // ===================== 生命周期 =====================

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("create_hand_made-server.json");
    }

    /** 服务端/单机启动时读盘；缺失则写出一份默认值。 */
    public static void load() {
        Path path = file();
        JsonObject root = new JsonObject();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null && parsed.isJsonObject()) {
                    root = parsed.getAsJsonObject();
                }
            } catch (Exception ex) {
                CreateHandMade.LOGGER.warn("Failed to read {}", path, ex);
            }
        }
        INSTANCE.apply(root);
        INSTANCE.save();
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(), writer);
            }
        } catch (IOException ex) {
            CreateHandMade.LOGGER.warn("Failed to write {}", path, ex);
        }
    }

    // ===================== 序列化（供网络同步） =====================

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        for (Value<?> value : ALL_VALUES) {
            root.add(value.path, value.toJson());
        }
        return root;
    }

    /** 从 JSON 覆盖内存值（缺省键保持当前值）。 */
    public void apply(JsonObject root) {
        for (Value<?> value : ALL_VALUES) {
            if (root.has(value.path) && root.get(value.path).isJsonPrimitive()) {
                try {
                    value.fromJson(root.getAsJsonPrimitive(value.path));
                } catch (Exception ex) {
                    CreateHandMade.LOGGER.warn("Invalid value for config key '{}' — keeping current", value.path);
                }
            }
        }
    }

    // ===================== 值类型 =====================

    public abstract static class Value<T> {
        private final String path;
        private final T defaultValue;
        protected T current;

        private Value(String path, T defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.current = defaultValue;
            ALL_VALUES.add(this);
        }

        public T get() {
            return current;
        }

        public T getDefault() {
            return defaultValue;
        }

        public void set(T value) {
            this.current = clamp(value);
        }

        public String getPath() {
            return path;
        }

        protected abstract T clamp(T value);

        protected abstract JsonPrimitive toJson();

        protected abstract void fromJson(JsonPrimitive primitive);
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;

        private IntValue(String path, int defaultValue, int min, int max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        protected Integer clamp(Integer value) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected JsonPrimitive toJson() {
            return new JsonPrimitive(current);
        }

        @Override
        protected void fromJson(JsonPrimitive primitive) {
            set(primitive.getAsInt());
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        private DoubleValue(String path, double defaultValue, double min, double max) {
            super(path, defaultValue);
            this.min = min;
            this.max = max;
        }

        public double getD() {
            return current;
        }

        @Override
        protected Double clamp(Double value) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected JsonPrimitive toJson() {
            return new JsonPrimitive(current);
        }

        @Override
        protected void fromJson(JsonPrimitive primitive) {
            set(primitive.getAsDouble());
        }
    }

    public static final class BoolValue extends Value<Boolean> {
        private BoolValue(String path, boolean defaultValue) {
            super(path, defaultValue);
        }

        @Override
        protected Boolean clamp(Boolean value) {
            return value;
        }

        @Override
        protected JsonPrimitive toJson() {
            return new JsonPrimitive(current);
        }

        @Override
        protected void fromJson(JsonPrimitive primitive) {
            set(primitive.getAsBoolean());
        }
    }
}

package com.alben.createhandmade;

import net.minecraftforge.common.ForgeConfigSpec; // 1. 修改导入
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC; // 2. 类型改为 ForgeConfigSpec

    static {
        // 3. 使用 ForgeConfigSpec.Builder
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config(ForgeConfigSpec.Builder builder) { // 4. 参数类型同步修改
        // 预留
    }
}
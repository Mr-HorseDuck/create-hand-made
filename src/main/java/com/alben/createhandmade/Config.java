package com.alben.createhandmade;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    /** 手锯设置 */
    public final ForgeConfigSpec.BooleanValue enableTreeFelling;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config(ForgeConfigSpec.Builder builder) {
        builder.comment("Create: Hand Made 配置").push("create_hand_made");

        // ==================== 手锯 ====================
        builder.comment("手锯设置").push("hand_saw");

        enableTreeFelling = builder
                .comment("是否开启手锯的整树砍伐功能",
                         "true  = 潜行+左键砍树时，会砍倒整棵树",
                         "false = 只会破坏单个方块",
                         "默认: true")
                .define("enable_tree_felling", true);

        builder.pop(); // hand_saw
        builder.pop(); // create_hand_made
    }
}
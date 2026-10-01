package com.alben.createhandmade;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Config INSTANCE;
    public static final ModConfigSpec SPEC;

    /** 手锯：破坏原木时是否连锁砍掉整棵树。 */
    public final ModConfigSpec.BooleanValue enableTreeFelling;

    static {
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config(ModConfigSpec.Builder builder) {
        enableTreeFelling = builder
                .comment("Allow the Hand Saw to fell the entire tree when breaking a log.",
                        "When false, breaking a log breaks only that single block (vanilla behaviour).",
                        "This only affects the chain-felling; cutting recipes and stripping are unaffected.")
                .translation("create_hand_made.configuration.enable_tree_felling")
                .define("enable_tree_felling", true);
    }
}
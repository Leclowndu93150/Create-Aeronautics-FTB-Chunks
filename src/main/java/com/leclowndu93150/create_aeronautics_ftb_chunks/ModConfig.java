package com.leclowndu93150.create_aeronautics_ftb_chunks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ALLOW_PLOT_CHUNK_FORCE_LOAD;

    static {
        BUILDER.comment("Contraption force loading settings. These are dangerous — misuse can cause severe server lag.");
        BUILDER.push("force_loading");

        ALLOW_PLOT_CHUNK_FORCE_LOAD = BUILDER
                .comment(
                        "Allow players to force-load the plot chunks (the real-world anchor chunks) of a claimed contraption via FTB Chunks.",
                        "This adds a persistent Sable loading ticket so the contraption remains loaded and ticking when no players are nearby.",
                        "Respects FTB Chunks force-load limits and offline force-load settings.",
                        "WARNING: Force-loaded contraptions continue simulating physics and can cause server lag if overused."
                )
                .define("allow_plot_chunk_force_load", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}

package com.maxleiter.tilefinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TileFinder.MODID)
public class Config {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // Visual settings
        public static final ModConfigSpec.DoubleValue HELIX_RADIUS = BUILDER
                        .comment("Radius of the double helix effect")
                        .translation("tilefinder.configuration.helixRadius")
                        .defineInRange("helixRadius", 0.3, 0.1, 1.0);

        public static final ModConfigSpec.DoubleValue HELIX_SPEED = BUILDER
                        .comment("Speed of the helix rotation")
                        .translation("tilefinder.configuration.helixSpeed")
                        .defineInRange("helixSpeed", 0.5, 0.0, 2.0);

        public static final ModConfigSpec.DoubleValue ARC_SCALE = BUILDER
                        .comment("Scale of the arc in the beam path")
                        .translation("tilefinder.configuration.arcScale")
                        .defineInRange("arcScale", 5.0, 0.0, 20.0);

        public static final ModConfigSpec.BooleanValue ENABLE_BEAM = BUILDER
                        .comment("Enable the visual beam effect")
                        .translation("tilefinder.configuration.enableBeam")
                        .define("enableBeam", true);

        // Color settings
        public static final ModConfigSpec.IntValue HELIX_COLOR_1_RED = BUILDER
                        .comment("Red component of first helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor1Red")
                        .defineInRange("helixColor1Red", 180, 0, 255);

        public static final ModConfigSpec.IntValue HELIX_COLOR_1_GREEN = BUILDER
                        .comment("Green component of first helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor1Green")
                        .defineInRange("helixColor1Green", 60, 0, 255);

        public static final ModConfigSpec.IntValue HELIX_COLOR_1_BLUE = BUILDER
                        .comment("Blue component of first helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor1Blue")
                        .defineInRange("helixColor1Blue", 255, 0, 255);

        public static final ModConfigSpec.IntValue HELIX_COLOR_2_RED = BUILDER
                        .comment("Red component of second helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor2Red")
                        .defineInRange("helixColor2Red", 255, 0, 255);

        public static final ModConfigSpec.IntValue HELIX_COLOR_2_GREEN = BUILDER
                        .comment("Green component of second helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor2Green")
                        .defineInRange("helixColor2Green", 120, 0, 255);

        public static final ModConfigSpec.IntValue HELIX_COLOR_2_BLUE = BUILDER
                        .comment("Blue component of second helix strand color (0-255)")
                        .translation("tilefinder.configuration.helixColor2Blue")
                        .defineInRange("helixColor2Blue", 255, 0, 255);

        // GUI settings
        public static final ModConfigSpec.IntValue DEFAULT_RADIUS = BUILDER
                        .comment("Default search radius for tile entities")
                        .translation("tilefinder.configuration.defaultRadius")
                        .defineInRange("defaultRadius", 24, 1, 64);

        // Favorites
        public static final ModConfigSpec.ConfigValue<List<? extends String>> FAVORITES = BUILDER
                        .comment("List of favorite tile entity IDs")
                        .translation("tilefinder.configuration.favorites")
                        .defineListAllowEmpty(List.of("favorites"), () -> List.of("minecraft:chest"),
                                        obj -> obj instanceof String);

        public static final ModConfigSpec SPEC = BUILDER.build();

        // Runtime values with defaults
        public static double helixRadius = 0.3;
        public static double helixSpeed = 0.5;
        public static double arcScale = 5.0;
        public static boolean enableBeam = true;
        public static int helixColor1Red = 180;
        public static int helixColor1Green = 60;
        public static int helixColor1Blue = 255;
        public static int helixColor2Red = 255;
        public static int helixColor2Green = 120;
        public static int helixColor2Blue = 255;
        public static int defaultRadius = 24;
        public static Set<String> favorites = new HashSet<>();

        @SubscribeEvent
        public static void onLoad(ModConfigEvent event) {
                helixRadius = HELIX_RADIUS.get();
                helixSpeed = HELIX_SPEED.get();
                arcScale = ARC_SCALE.get();
                enableBeam = ENABLE_BEAM.get();
                helixColor1Red = HELIX_COLOR_1_RED.get();
                helixColor1Green = HELIX_COLOR_1_GREEN.get();
                helixColor1Blue = HELIX_COLOR_1_BLUE.get();
                helixColor2Red = HELIX_COLOR_2_RED.get();
                helixColor2Green = HELIX_COLOR_2_GREEN.get();
                helixColor2Blue = HELIX_COLOR_2_BLUE.get();
                defaultRadius = DEFAULT_RADIUS.get();

                // Load favorites
                favorites.clear();
                favorites.addAll(FAVORITES.get());

                System.out.println("TileFinder Config loaded: enableBeam=" + enableBeam + ", arcScale="
                                + arcScale + ", helixRadius=" + helixRadius);
        }

        public static void saveFavorites() {
                FAVORITES.set(List.copyOf(favorites));
        }
}

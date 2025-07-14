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
                        .defineInRange("helixRadius", 0.3, 0.1, 1.0);

        public static final ModConfigSpec.DoubleValue HELIX_SPEED = BUILDER
                        .comment("Speed of the helix rotation")
                        .defineInRange("helixSpeed", 0.5, 0.0, 2.0);

        public static final ModConfigSpec.DoubleValue ARC_SCALE = BUILDER
                        .comment("Scale of the arc in the beam path")
                        .defineInRange("arcScale", 5.0, 0.0, 20.0);

        public static final ModConfigSpec.BooleanValue ENABLE_BEAM = BUILDER
                        .comment("Enable the visual beam effect")
                        .define("enableBeam", true);

        // GUI settings
        public static final ModConfigSpec.IntValue DEFAULT_RADIUS = BUILDER
                        .comment("Default search radius for tile entities")
                        .defineInRange("defaultRadius", 24, 1, 64);

        // Favorites
        public static final ModConfigSpec.ConfigValue<List<? extends String>> FAVORITES = BUILDER
                        .comment("List of favorite tile entity IDs")
                        .defineListAllowEmpty(List.of("favorites"), () -> List.of("minecraft:chest"),
                                        obj -> obj instanceof String);

        public static final ModConfigSpec SPEC = BUILDER.build();

        // Runtime values with defaults
        public static double helixRadius = 0.3;
        public static double helixSpeed = 0.5;
        public static double arcScale = 5.0;
        public static boolean enableBeam = true;
        public static int defaultRadius = 24;
        public static Set<String> favorites = new HashSet<>();

        @SubscribeEvent
        static void onLoad(final ModConfigEvent event) {
                if (event.getConfig().getSpec() == SPEC) {
                        helixRadius = HELIX_RADIUS.get();
                        helixSpeed = HELIX_SPEED.get();
                        arcScale = ARC_SCALE.get();
                        enableBeam = ENABLE_BEAM.get();
                        defaultRadius = DEFAULT_RADIUS.get();

                        favorites.clear();
                        favorites.addAll(FAVORITES.get());

                        System.out.println("TileFinder Config loaded: enableBeam=" + enableBeam + ", arcScale="
                                        + arcScale + ", helixRadius=" + helixRadius);
                }
        }

        public static void saveFavorites() {
                FAVORITES.set(List.copyOf(favorites));
        }
}

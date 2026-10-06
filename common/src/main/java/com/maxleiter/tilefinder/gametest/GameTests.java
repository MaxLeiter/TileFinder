package com.maxleiter.tilefinder.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.maxleiter.tilefinder.TileFinder;

import net.minecraft.gametest.framework.GameTestHelper;
//? if <26 {
/*import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
*///?}

/**
 * How a GameTest is declared on each Minecraft version. From 26.x tests are test functions in a registry
 * ({@link #all()}, registered by the loaders) and their test instances are data
 * ({@code data/tilefinder/test_instance/<name>.json}). On 1.21.1 they come from a {@link GameTestGenerator} that makes
 * a test of each declared body, in the same structure and with the ticks of the 26.x instances; NeoForge registers
 * this class in {@code RegisterGameTestsEvent}, Fabric lists it with {@code GameTestRegistry}.
 */
public final class GameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    /** Fabric makes an instance of each registered test class. */
    public GameTests() {
    }

    static void add(String name, Consumer<GameTestHelper> body) {
        TESTS.put(name, body);
    }

    public static Map<String, Consumer<GameTestHelper>> all() {
        TileFinderGameTests.init();
        return TESTS;
    }

    //? if <26 {
    /*@GameTestGenerator
    public static List<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        all().forEach((name, body) -> tests.add(new TestFunction(TileFinder.MOD_ID, TileFinder.MOD_ID + ":" + name,
                TileFinder.MOD_ID + ":test/arena", 40, 1, true, body)));
        return tests;
    }
    *///?}
}

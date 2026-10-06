package com.maxleiter.tilefinder.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.maxleiter.tilefinder.TileFinder;

import dev.vellum.mod.client.VellumAutomation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev-only visual check ({@code ./gradlew :neoforge:26.3:runClient -Pautopilot}, or any other loader and version, i.e.
 * {@code -Dtilefinder.autopilot=true}): creates a creative superflat world, builds a scene of block entities with
 * commands, drives the finder (list, search, an expanded group, settings), tracks a group, turns away from it, and
 * saves screenshots into {@code runs/client/screenshots/}: finder-list, finder-search, finder-expanded,
 * finder-settings and beam. Then quits. Does nothing without the property. A step that cannot do its job fails the run
 * (the log ends with "FAILED") rather than going on.
 */
public final class DevAutopilot {
    public static final boolean ENABLED = System.getProperty("tilefinder.autopilot") != null;
    private static final String WORLD = "tilefinder-autopilot";
    private static final int MAX_POLL = 200;

    private static boolean started, loaded, finished;
    private static final Deque<Runnable> steps = new ArrayDeque<>();
    private static int wait;
    private static int failures;
    private static BlockPos origin = BlockPos.ZERO;

    private DevAutopilot() {
    }

    /** Client tick hook; the loaders call it through {@link TileFinderClient#tick}. */
    public static void tick(Minecraft mc) {
        if (!ENABLED || finished) return;
        if (!started) {
            // A fresh run directory first shows the accessibility onboarding screen instead of the title screen.
            if (!(McClient.screen() instanceof TitleScreen || McClient.screen() instanceof AccessibilityOnboardingScreen)) return;
            started = true;
            log("creating world " + WORLD);
            mc.options.onboardingAccessibilityFinished();
            // The window may never have focus while the autopilot runs.
            mc.options.pauseOnLostFocus = false;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
            //? if >=26 {
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                    WorldPresets::createTestWorldDimensions, McClient.screen());
            //?} else {
            /*LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                    registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), McClient.screen());
            *///?}
            return;
        }
        if (!loaded) {
            if (mc.player == null || mc.level == null) return;
            loaded = true;
            plan(mc);
            // Chunks around the spawn, and the player landing on the ground.
            wait = 100;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Runnable step = steps.poll();
        if (step == null) {
            finished = true;
            if (failures == 0) log("finished, no failures");
            else TileFinder.LOG.error("TileFinder autopilot: FAILED, {} failed steps", failures);
            mc.stop();
            return;
        }
        try {
            step.run();
        } catch (RuntimeException e) {
            fail("a step threw", e);
        }
    }

    // ---- The script ----

    private static void plan(Minecraft mc) {
        steps.add(() -> {
            origin = mc.player.blockPosition();
            log("player on the ground at " + origin.toShortString());
            // The first chat messages and toasts clutter the shots.
            mc.options.guiScale().set(2);
            //? if >=26 {
            mc.resizeGui();
            //?} else
            /*mc.resizeDisplay();*/
            wait = 5;
        });
        for (String command : scene()) command(mc, command);
        steps.add(() -> wait = 40);

        steps.add(() -> {
            log("opening the finder");
            FinderPage.open(null, "list");
        });
        settle("the finder page");
        // The scan comes with the page: the groups are the scene's block entity kinds.
        steps.add(() -> onPage("list the groups", page -> log("groups: " + eval(page, "[...document.querySelectorAll('.g-name')].map(e => e.textContent).join(' | ')")
                + "; summary: " + page.text(".summary").orElse("?"))));
        shoot(mc, "finder-list");

        steps.add(() -> onPage("search", page -> {
            check(page.click("#search"), "click the search box");
            page.type("diam");
        }));
        settle("the search");
        steps.add(() -> onPage("search", page -> log("after searching for diam: " + eval(page, "[...document.querySelectorAll('.g-name')].map(e => e.textContent).join(' | ')")
                + "; spots: " + eval(page, "[...document.querySelectorAll('.spot .label')].map(e => e.textContent).join(' | ')"))));
        shoot(mc, "finder-search");

        // Clear the search, open the hopper group (a run of eight is one spot with members) and its members.
        steps.add(() -> onPage("clear", page -> check(page.click(".search .x"), "click the clear button")));
        settle("the cleared search");
        steps.add(() -> onPage("expand", page -> {
            int index = groupIndex(page, "Hopper");
            check(index > 0, "find the Hopper group");
            if (index > 0) check(page.click(".results .group:nth-child(" + index + ") .g-head"), "expand the Hopper group");
        }));
        settle("the expanded group");
        steps.add(() -> onPage("members", page -> {
            int index = groupIndex(page, "Hopper");
            if (index > 0) check(page.click(".results .group:nth-child(" + index + ") .spot-actions .mini"), "expand the hopper spot's members");
        }));
        settle("the expanded members");
        steps.add(() -> onPage("leave", VellumAutomation::leave));
        shoot(mc, "finder-expanded");

        steps.add(() -> onPage("settings", page -> check(page.click(".tabs button:nth-child(2)"), "click the Settings tab")));
        settle("the settings tab");
        steps.add(() -> onPage("leave", VellumAutomation::leave));
        shoot(mc, "finder-settings");

        steps.add(() -> onPage("list", page -> check(page.click(".tabs button:nth-child(1)"), "click the List tab")));
        settle("the list tab");
        // Track every chest through the page's own button; that closes the screen.
        steps.add(() -> onPage("track", page -> {
            int index = groupIndex(page, "Chest");
            check(index > 0, "find the Chest group");
            if (index > 0) check(page.click(".results .group:nth-child(" + index + ") .track"), "click Track all on Chests");
        }));
        steps.add(() -> wait = 5);
        steps.add(() -> {
            check(McClient.screen() == null, "the finder closed after tracking");
            check(Tracker.active(), "the tracker has targets: " + Tracker.targets());
            log("tracking " + Tracker.targets());
            // The scene is south of the player: face north.
            mc.player.connection.sendCommand("tp @s ~ ~ ~ 180 0");
            wait = 60;
        });
        steps.add(() -> {
            clearChat(mc);
            wait = 3;
        });
        shot(mc, "beam");
    }

    /** The scene: everything south of the player, in a 13 by 8 patch, as /setblock and /fill commands. */
    private static String[] scene() {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        String named = //? if >=26
                "{CustomName:\"Diamonds\"}";
                //? if <26
                /*"{CustomName:'\"Diamonds\"'}";*/
        return new String[]{
                "time set noon", "weather clear",
                // A double chest, a single chest, and a chest renamed "Diamonds".
                set(x - 1, y, z + 4, "chest[facing=south,type=right]"), set(x, y, z + 4, "chest[facing=south,type=left]"),
                set(x + 3, y, z + 4, "chest[facing=south]"),
                set(x - 4, y, z + 4, "chest[facing=south]" + named),
                // Machines.
                set(x + 5, y, z + 3, "furnace[facing=west]"), set(x + 5, y, z + 5, "blast_furnace[facing=west]"),
                set(x + 5, y, z + 7, "smoker[facing=west]"), set(x - 6, y, z + 3, "enchanting_table"),
                set(x - 6, y, z + 5, "brewing_stand"), set(x - 6, y, z + 7, "ender_chest[facing=east]"),
                // A bed (a block entity before 26.2), and a sign on a wall.
                set(x - 3, y, z + 7, "red_bed[facing=south,part=foot]"), set(x - 3, y, z + 8, "red_bed[facing=south,part=head]"),
                set(x + 2, y, z + 9, "stone"), set(x + 2, y, z + 8, "oak_wall_sign[facing=north]"),
                // A run of eight hoppers, and a wall of barrels.
                "fill " + (x - 4) + " " + y + " " + (z + 10) + " " + (x + 3) + " " + y + " " + (z + 10) + " hopper",
                "fill " + (x + 6) + " " + y + " " + (z + 9) + " " + (x + 10) + " " + (y + 2) + " " + (z + 9) + " barrel[facing=north]",
        };
    }

    private static String set(int x, int y, int z, String block) {
        return "setblock " + x + " " + y + " " + z + " " + block;
    }

    // ---- Steps ----

    private static void command(Minecraft mc, String command) {
        steps.add(() -> {
            mc.player.connection.sendCommand(command);
            wait = 2;
        });
    }

    private static void onPage(String what, Consumer<VellumAutomation> action) {
        Optional<VellumAutomation> page = VellumAutomation.screen();
        if (page.isEmpty()) fail("no page open for " + what, null);
        else action.accept(page.get());
    }

    /** Waits until the open page has settled; goes on (and says so) after a while. */
    private static void settle(String what) {
        until(what + " settled", () -> VellumAutomation.screen().map(VellumAutomation::settled).orElse(false), 4);
    }

    private static void until(String what, BooleanSupplier done, int minTicks) {
        steps.add(new Runnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks >= minTicks && done.getAsBoolean()) log(what + " (" + ticks + " ticks)");
                else if (ticks < MAX_POLL) steps.addFirst(this);
                else fail("gave up waiting until " + what, null);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        // A page keeps animating (the caret, hover); a few ticks after settling are enough.
        steps.add(() -> wait = 10);
        shot(mc, name);
    }

    private static void shot(Minecraft mc, String name) {
        steps.add(() -> {
            clearChat(mc);
            //? if >=26 {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> log(msg.getString()));
            //?} else
            /*Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log(msg.getString()));*/
            wait = 5;
        });
    }

    private static void clearChat(Minecraft mc) {
        //? if >=26 {
        mc.gui.hud.getChat().clearMessages(false);
        //?} else
        /*mc.gui.getChat().clearMessages(false);*/
    }

    /** The 1-based position of the first group named {@code name} in the results, or 0. */
    private static int groupIndex(VellumAutomation page, String name) {
        String names = eval(page, "[...document.querySelectorAll('.results .group .g-name')].map(e => e.textContent).join('|')");
        String[] list = names.split("\\|");
        for (int i = 0; i < list.length; i++) {
            if (list[i].equals(name)) return i + 1;
        }
        return 0;
    }

    private static String eval(VellumAutomation page, String js) {
        Optional<JsonElement> value = page.eval(js);
        return value.map(JsonElement::getAsString).orElse("<no value>");
    }

    private static void check(boolean ok, String what) {
        if (!ok) fail(what + " failed", null);
    }

    private static void fail(String what, Throwable cause) {
        failures++;
        if (cause == null) TileFinder.LOG.error("TileFinder autopilot: {}", what);
        else TileFinder.LOG.error("TileFinder autopilot: {}", what, cause);
    }

    private static void log(String message) {
        TileFinder.LOG.info("TileFinder autopilot: {}", message);
    }
}

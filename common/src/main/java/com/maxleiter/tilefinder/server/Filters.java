package com.maxleiter.tilefinder.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.maxleiter.tilefinder.platform.Platform;
import com.maxleiter.tilefinder.scan.Finder;

import net.minecraft.network.chat.Component;

/**
 * The search the client page has: every word must be in a group's name, block id or item key, or in a spot's custom
 * name (a chest called "Diamonds"); {@code @text} matches the mod's namespace or display name.
 */
final class Filters {
    private Filters() {
    }

    static List<Finder.Group> apply(List<Finder.Group> groups, String query) {
        List<String> words = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (word.charAt(0) == '@' && word.length() > 1) mods.add(word.substring(1));
            else words.add(word);
        }
        if (words.isEmpty() && mods.isEmpty()) return groups;

        List<Finder.Group> result = new ArrayList<>();
        for (Finder.Group group : groups) {
            String namespace = group.blockId().getNamespace();
            String mod = (namespace + " " + Platform.INSTANCE.modName(namespace)).toLowerCase(Locale.ROOT);
            if (!mods.stream().allMatch(mod::contains)) continue;

            String text = (group.name().getString() + " " + group.blockId() + " " + group.key()).toLowerCase(Locale.ROOT);
            List<String> loose = words.stream().filter(word -> !text.contains(word)).toList();
            if (loose.isEmpty()) {
                result.add(group);
                continue;
            }
            List<Finder.Spot> spots = group.spots().stream().filter(spot -> loose.stream().allMatch(spotNames(spot)::contains)).toList();
            if (spots.isEmpty()) continue;
            result.add(new Finder.Group(group.key(), group.block(), group.blockId(), group.icon(), group.name(),
                    group.holds(), group.decorative(), spots));
        }
        return result;
    }

    static String spotNames(Finder.Spot spot) {
        StringBuilder names = new StringBuilder();
        for (Finder.Member member : spot.members()) {
            Component name = member.customName();
            if (name != null) names.append(name.getString().toLowerCase(Locale.ROOT)).append(' ');
        }
        return names.toString();
    }
}

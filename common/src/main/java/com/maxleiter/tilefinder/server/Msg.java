package com.maxleiter.tilefinder.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text sent to players who may not have TileFinder installed. A vanilla client has no tilefinder lang file, so every
 * message carries its English text as the translation fallback; the same keys are in en_us.json for clients that do.
 */
final class Msg {
    private Msg() {
    }

    static MutableComponent t(String key, String english, Object... args) {
        return Component.translatableWithFallback(key, english, args);
    }

    /** Item text: no italics (the default for names and lore), in this colour. */
    static MutableComponent item(MutableComponent text, ChatFormatting colour) {
        return text.withStyle(style -> style.withItalic(false).withColor(colour));
    }

    static MutableComponent item(Component text) {
        return text.copy().withStyle(style -> style.withItalic(false));
    }
}

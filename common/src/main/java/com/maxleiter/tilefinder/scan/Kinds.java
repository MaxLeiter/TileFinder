package com.maxleiter.tilefinder.scan;

import java.util.List;
import java.util.Set;

import com.maxleiter.tilefinder.Mc;
import com.maxleiter.tilefinder.TileFinder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Which block entity types are left out, and which are decoration rather than something you go looking for.
 * Packs and servers extend both with the {@code tilefinder:hidden} and {@code tilefinder:decorative} block entity type
 * tags; players extend them in the client settings.
 */
public final class Kinds {
    public static final TagKey<BlockEntityType<?>> HIDDEN_TAG = TagKey.create(Registries.BLOCK_ENTITY_TYPE, TileFinder.id("hidden"));
    public static final TagKey<BlockEntityType<?>> DECORATIVE_TAG = TagKey.create(Registries.BLOCK_ENTITY_TYPE, TileFinder.id("decorative"));

    /** A piston's moving block exists for the two ticks the block is moving. */
    public static final List<String> DEFAULT_HIDDEN = List.of("minecraft:piston");

    public static final List<String> DEFAULT_DECORATIVE = List.of(
            "minecraft:sign", "minecraft:hanging_sign", "minecraft:banner", "minecraft:skull", "minecraft:bed",
            "minecraft:decorated_pot", "minecraft:bell", "minecraft:copper_golem_statue");

    private final Set<String> hidden;
    private final Set<String> decorative;

    public Kinds(Set<String> hidden, Set<String> decorative) {
        this.hidden = hidden;
        this.decorative = decorative;
    }

    public static Kinds defaults() {
        return new Kinds(Set.copyOf(DEFAULT_HIDDEN), Set.copyOf(DEFAULT_DECORATIVE));
    }

    public boolean hidden(BlockEntityType<?> type) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(type).is(HIDDEN_TAG) || hidden.contains(Mc.typeId(type).toString());
    }

    public boolean decorative(BlockEntityType<?> type) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(type).is(DECORATIVE_TAG) || decorative.contains(Mc.typeId(type).toString());
    }
}

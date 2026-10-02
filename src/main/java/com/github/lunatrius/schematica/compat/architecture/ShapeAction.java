package com.github.lunatrius.schematica.compat.architecture;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/** One right-click the printer should make for an ArchitectureCraft shape: place it, or fix it up with a tool. */
public final class ShapeAction {

    public enum Type {
        /** Place the shape item against a neighbouring block. */
        PLACE,
        /** Hammer: sneaking turns the side, otherwise the turn (and banister offset). */
        HAMMER,
        /** Right-click the shape with a glass pane or cladding (not sneaking). */
        SECONDARY,
        /** Chisel: an edge zone toggles a connection, the centre takes the second material off. */
        CHISEL
    }

    public final Type type;
    /** Item to hold for the click. */
    public final ItemStack item;
    /** Match the held item's NBT too (shape items and cladding differ only by NBT). */
    public final boolean matchNbt;
    public final boolean sneak;
    /** Block clicked, its face (vanilla side index) and the hit point in world coordinates. */
    public final int x, y, z, side;
    public final Vec3 hit;
    public final String description;

    public ShapeAction(Type type, ItemStack item, boolean matchNbt, boolean sneak, int x, int y, int z, int side,
        Vec3 hit, String description) {
        this.type = type;
        this.item = item;
        this.matchNbt = matchNbt;
        this.sneak = sneak;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.hit = hit;
        this.description = description;
    }
}

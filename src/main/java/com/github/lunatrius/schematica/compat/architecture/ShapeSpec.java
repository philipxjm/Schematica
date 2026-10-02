package com.github.lunatrius.schematica.compat.architecture;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

/**
 * An ArchitectureCraft shape block's state, as its tile entity saves it (TileShape.writeToNBT): which shape, the
 * material, orientation, banister offset, second material (window glass, cladding) and chiselled-off connections.
 * Read from NBT, so it needs no ArchitectureCraft classes.
 */
public final class ShapeSpec {

    /** On ArchitectureCraft:shapeSE (glowing) rather than ArchitectureCraft:shape. */
    public final boolean glow;
    public final int shape;
    public final String baseName;
    public final int baseData;
    public final int side, turn;
    /** Sixteenths of a block, as saved. */
    public final int offsetX;
    /** Second material, or null. */
    public final String name2;
    public final int data2;
    public final int disconnected;

    public ShapeSpec(boolean glow, NBTTagCompound tag) {
        this.glow = glow;
        this.shape = tag.getInteger("Shape");
        this.baseName = tag.getString("BaseName");
        this.baseData = tag.getInteger("BaseData");
        this.side = tag.getByte("side");
        this.turn = tag.getByte("turn");
        this.offsetX = tag.getByte("offsetX");
        final String n2 = tag.getString("Name2");
        this.name2 = n2 == null || n2.isEmpty() ? null : n2;
        this.data2 = this.name2 == null ? 0 : tag.getInteger("Data2");
        this.disconnected = tag.getInteger("Disconnected");
    }

    /** Same shape item: shape, material and glow (what the sawbench makes). */
    public boolean sameItem(ShapeSpec o) {
        return o != null && this.glow == o.glow
            && this.shape == o.shape
            && this.baseData == o.baseData
            && Objects.equals(this.baseName, o.baseName);
    }

    public boolean sameOrientation(ShapeSpec o) {
        return this.side == o.side && this.turn == o.turn && this.offsetX == o.offsetX;
    }

    public boolean sameSecondary(ShapeSpec o) {
        return Objects.equals(this.name2, o.name2) && this.data2 == o.data2;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ShapeSpec)) return false;
        final ShapeSpec o = (ShapeSpec) obj;
        return sameItem(o) && sameOrientation(o) && sameSecondary(o) && this.disconnected == o.disconnected;
    }

    @Override
    public int hashCode() {
        return Objects.hash(glow, shape, baseName, baseData, side, turn, offsetX, name2, data2, disconnected);
    }

    @Override
    public String toString() {
        return "shape " + this.shape
            + (this.glow ? " (glow)" : "")
            + " of "
            + this.baseName
            + "@"
            + this.baseData
            + " side "
            + this.side
            + " turn "
            + this.turn
            + (this.offsetX != 0 ? " offset " + this.offsetX + "/16" : "")
            + (this.name2 != null ? " with " + this.name2 + "@" + this.data2 : "")
            + (this.disconnected != 0 ? " disconnected " + Integer.toBinaryString(this.disconnected) : "");
    }
}

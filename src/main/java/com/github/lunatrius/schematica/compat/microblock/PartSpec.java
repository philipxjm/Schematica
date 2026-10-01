package com.github.lunatrius.schematica.compat.microblock;

/**
 * One Forge Multipart microblock saved in a schematic: the "parts" entries of a "savedMultipart" tile entity.
 * shape = size << 4 | slot, size in eighths of a block.
 */
public final class PartSpec {

    public final String id;
    public final String material;
    public final int shape;

    public PartSpec(String id, String material, int shape) {
        this.id = id;
        this.material = material;
        this.shape = shape & 0xff;
    }

    public int size() {
        return this.shape >> 4;
    }

    @Override
    public String toString() {
        return this.id + "/" + this.material + "/" + this.shape;
    }
}

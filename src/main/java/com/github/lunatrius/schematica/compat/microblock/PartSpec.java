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
    public boolean equals(Object o) {
        if (!(o instanceof PartSpec)) {
            return false;
        }
        final PartSpec other = (PartSpec) o;
        return this.shape == other.shape && this.id.equals(other.id) && this.material.equals(other.material);
    }

    @Override
    public int hashCode() {
        return (this.id.hashCode() * 31 + this.material.hashCode()) * 31 + this.shape;
    }

    @Override
    public String toString() {
        return this.id + "/" + this.material + "/" + this.shape;
    }
}

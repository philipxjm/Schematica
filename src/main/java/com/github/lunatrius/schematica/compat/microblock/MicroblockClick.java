package com.github.lunatrius.schematica.compat.microblock;

import net.minecraft.util.Vec3;

/**
 * Where to click to place one microblock: the block and face to right-click, the point on that face, and the look
 * direction that makes the server's ray trace (Forge Multipart re-traces from the player's eyes) hit that point.
 */
public final class MicroblockClick {

    public final int x, y, z, side;
    public final Vec3 hit;
    public final float yaw, pitch;

    public MicroblockClick(int x, int y, int z, int side, Vec3 hit, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.hit = hit;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

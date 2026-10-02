package com.github.lunatrius.schematica.compat.architecture;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * ArchitectureCraft shape printing. Every shape is one block (ArchitectureCraft:shape, or shapeSE when it glows)
 * whose tile entity holds the shape, material and orientation, so block and meta say nothing: the printer compares
 * tile entities, places the right shape item, steers the orientation through the click's face and hit point, and
 * fixes the rest with the hammer, chisel and a right-click with the second material.
 */
public interface IArchitecture {

    /** What the schematic wants in a world space, for the chisel's two-sided connection toggles. */
    interface Wanted {

        /** The shape the schematic wants at these world coordinates, or null if none. */
        ShapeSpec at(int x, int y, int z);
    }

    boolean isShape(Block block);

    /** The state of a shape block's tile entity, or null if it isn't one (or hasn't arrived yet). */
    ShapeSpec specOf(Block block, TileEntity tileEntity);

    /** The shape item the sawbench makes for this shape: block shape/shapeSE with Shape, BaseName, BaseData. */
    ItemStack itemFor(ShapeSpec spec);

    /** The item that applies the second material (glass pane, or cladding), or null if there is none. */
    ItemStack secondaryItemFor(ShapeSpec spec);

    /**
     * The next click towards `want` at world (x, y, z), where the world holds `have` (null: nothing placed yet; the
     * space is then free to place in). Null when nothing can be done now; `why` gets the reason.
     */
    ShapeAction nextAction(World world, EntityClientPlayerMP player, int x, int y, int z, ShapeSpec want,
        ShapeSpec have, Wanted wanted, StringBuilder why);

    /** Run the client-side half of a click with the player's sneak state as the server will see it. */
    void withSneak(EntityClientPlayerMP player, boolean sneak, Runnable click);
}

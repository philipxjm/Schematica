package com.github.lunatrius.schematica.compat.microblock;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Forge Multipart microblock support for the printer and the material list. {@link NoMicroblocks} when Forge
 * Microblocks is not installed, {@link MicroblocksPresent} when it is (loaded by name, so this mod still runs
 * without it).
 */
public interface IMicroblocks {

    /** True for Forge Multipart's container block. */
    boolean isMultipart(Block block);

    /** The microblocks a schematic tile entity holds; empty when it holds none. */
    List<PartSpec> partsOf(TileEntity tileEntity);

    /** The microblock item for a part (material in its NBT), or null. */
    ItemStack itemFor(PartSpec part);

    /** True if the world already has this exact part at the position. */
    boolean isPlaced(World world, int x, int y, int z, PartSpec part);

    /** The microblocks the world has at the position. */
    List<PartSpec> placedParts(World world, int x, int y, int z);

    /**
     * How to place the part at the world position (x, y, z) by right-clicking a face next to it (a full block's, or
     * an empty space's) while holding its item, or null if no reachable face gives exactly this part.
     */
    MicroblockClick planClick(World world, EntityPlayer player, int x, int y, int z, PartSpec part, double reach,
        BusySpaces busy);

    /**
     * Spaces the printer clicked parts into very recently. The server may already have a multipart there while the
     * client still shows air, so their faces must not be clicked as empty space: the server's ray would hit the new
     * part and add a part to that space instead.
     */
    interface BusySpaces {

        boolean isBusy(int x, int y, int z);
    }

    /**
     * What the server will place for this click, worked out the way it does: the player's current look (set it to
     * the click's yaw and pitch first), the clicked block's own ray trace from the server's eye position, then Forge
     * Multipart's placement logic. Null when it gives exactly the part at (x, y, z); otherwise a description of what
     * it would do instead.
     */
    String mismatch(World world, EntityPlayer player, int x, int y, int z, MicroblockClick click, PartSpec part,
        double reach);

    /** The eye position the clicks are aimed from, for logs. */
    String describeEye(EntityPlayer player);
}

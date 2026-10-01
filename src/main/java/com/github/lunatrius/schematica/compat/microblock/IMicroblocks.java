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
    MicroblockClick planClick(World world, EntityPlayer player, int x, int y, int z, PartSpec part, double reach);
}

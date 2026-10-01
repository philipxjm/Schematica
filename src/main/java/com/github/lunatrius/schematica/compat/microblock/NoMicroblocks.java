package com.github.lunatrius.schematica.compat.microblock;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NoMicroblocks implements IMicroblocks {

    @Override
    public boolean isMultipart(Block block) {
        return false;
    }

    @Override
    public List<PartSpec> partsOf(TileEntity tileEntity) {
        return Collections.emptyList();
    }

    @Override
    public ItemStack itemFor(PartSpec part) {
        return null;
    }

    @Override
    public boolean isPlaced(World world, int x, int y, int z, PartSpec part) {
        return false;
    }

    @Override
    public List<PartSpec> placedParts(World world, int x, int y, int z) {
        return Collections.emptyList();
    }

    @Override
    public MicroblockClick planClick(World world, EntityPlayer player, int x, int y, int z, PartSpec part, double reach,
        BusySpaces busy) {
        return null;
    }

    @Override
    public String mismatch(World world, EntityPlayer player, int x, int y, int z, MicroblockClick click, PartSpec part,
        double reach) {
        return "microblocks not installed";
    }

    @Override
    public String describeEye(EntityPlayer player) {
        return "";
    }
}

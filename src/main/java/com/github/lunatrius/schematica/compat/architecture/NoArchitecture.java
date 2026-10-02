package com.github.lunatrius.schematica.compat.architecture;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NoArchitecture implements IArchitecture {

    @Override
    public boolean isShape(Block block) {
        return false;
    }

    @Override
    public ShapeSpec specOf(Block block, TileEntity tileEntity) {
        return null;
    }

    @Override
    public ItemStack itemFor(ShapeSpec spec) {
        return null;
    }

    @Override
    public ItemStack secondaryItemFor(ShapeSpec spec) {
        return null;
    }

    @Override
    public ShapeAction nextAction(World world, EntityClientPlayerMP player, int x, int y, int z, ShapeSpec want,
        ShapeSpec have, Wanted wanted, StringBuilder why) {
        why.append("ArchitectureCraft not installed");
        return null;
    }

    @Override
    public void withSneak(EntityClientPlayerMP player, boolean sneak, Runnable click) {
        click.run();
    }
}

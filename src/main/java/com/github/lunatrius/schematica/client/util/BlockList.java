package com.github.lunatrius.schematica.client.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import com.github.lunatrius.core.entity.EntityHelper;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.compat.microblock.IMicroblocks;
import com.github.lunatrius.schematica.compat.microblock.PartSpec;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Reference;

public class BlockList {

    public List<WrappedItemStack> getList(final EntityPlayer player, final SchematicWorld world,
        final WorldClient mcWorld) {
        final List<WrappedItemStack> blockList = new ArrayList<>();

        if (world == null) {
            return blockList;
        }

        final MovingObjectPosition movingObjectPosition = new MovingObjectPosition(player);

        for (int y = 0; y < world.getHeight(); y++) {
            for (int x = 0; x < world.getWidth(); x++) {
                for (int z = 0; z < world.getLength(); z++) {
                    if (world.isRenderingLayer && y != world.renderingLayer) {
                        continue;
                    }

                    final Block block = world.getBlock(x, y, z);

                    if (block == Blocks.air || world.isAirBlock(x, y, z)) {
                        continue;
                    }

                    final int wx = world.position.x + x;
                    final int wy = world.position.y + y;
                    final int wz = world.position.z + z;

                    // Microblocks: one entry per part item (strip, cover...), told apart by material NBT.
                    final IMicroblocks microblocks = ClientProxy.microblocks;
                    if (microblocks.isMultipart(block)) {
                        for (PartSpec part : microblocks.partsOf(world.getTileEntity(x, y, z))) {
                            final ItemStack partStack = microblocks.itemFor(part);
                            if (partStack == null) {
                                continue;
                            }
                            final WrappedItemStack wrapped = findOrCreateWrappedItemStackFor(blockList, partStack);
                            if (microblocks.isPlaced(mcWorld, wx, wy, wz, part)) {
                                wrapped.placed++;
                            }
                            wrapped.total++;
                        }
                        continue;
                    }

                    final Block mcBlock = mcWorld.getBlock(wx, wy, wz);
                    final boolean isPlaced = block == mcBlock
                        && world.getBlockMetadata(x, y, z) == mcWorld.getBlockMetadata(wx, wy, wz);

                    ItemStack stack = null;

                    try {
                        stack = block.getPickBlock(movingObjectPosition, world, x, y, z, player);
                    } catch (final Exception e) {
                        Reference.logger.debug("Could not get the pick block for: {}", block, e);
                    }

                    if (stack == null || stack.getItem() == null) {
                        Reference.logger.debug("Could not find the item for: {}", block);
                        continue;
                    }

                    final WrappedItemStack wrappedItemStack = findOrCreateWrappedItemStackFor(blockList, stack);
                    if (isPlaced) {
                        wrappedItemStack.placed++;
                    }
                    wrappedItemStack.total++;
                }
            }
        }

        for (WrappedItemStack wrappedItemStack : blockList) {
            if (player.capabilities.isCreativeMode) wrappedItemStack.inventory = -1;
            else if (wrappedItemStack.itemStack.hasTagCompound())
                wrappedItemStack.inventory = countWithTag(player, wrappedItemStack.itemStack);
            else wrappedItemStack.inventory = EntityHelper.getItemCountInInventory(
                player.inventory,
                wrappedItemStack.itemStack.getItem(),
                wrappedItemStack.itemStack.getItemDamage());
        }

        return blockList;
    }

    /** Inventory count of items equal to the stack including NBT (microblocks differ only by material tag). */
    private static int countWithTag(final EntityPlayer player, final ItemStack itemStack) {
        int count = 0;
        for (final ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.isItemEqual(itemStack) && ItemStack.areItemStackTagsEqual(stack, itemStack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private WrappedItemStack findOrCreateWrappedItemStackFor(final List<WrappedItemStack> blockList,
        final ItemStack itemStack) {
        for (final WrappedItemStack wrappedItemStack : blockList) {
            if (wrappedItemStack.itemStack.isItemEqual(itemStack) && (!itemStack.hasTagCompound()
                || ItemStack.areItemStackTagsEqual(wrappedItemStack.itemStack, itemStack))) {
                return wrappedItemStack;
            }
        }

        final WrappedItemStack wrappedItemStack = new WrappedItemStack(itemStack.copy());
        blockList.add(wrappedItemStack);
        return wrappedItemStack;
    }

    public static class WrappedItemStack {

        public ItemStack itemStack;
        public int placed;
        public int total;
        public int inventory;

        public WrappedItemStack(final ItemStack itemStack) {
            this(itemStack, 0, 0);
        }

        public WrappedItemStack(final ItemStack itemStack, final int placed, final int total) {
            this.itemStack = itemStack;
            this.placed = placed;
            this.total = total;
        }

        public String getItemStackDisplayName() {
            return this.itemStack.getItem()
                .getItemStackDisplayName(this.itemStack);
        }

        public String getFormattedAmount() {
            final char color = this.placed < this.total ? 'c' : 'a';
            return String.format(
                "\u00a7%c%s\u00a7r/%s",
                color,
                getFormattedStackAmount(itemStack, this.placed),
                getFormattedStackAmount(itemStack, this.total));
        }

        public String getFormattedAmountRequired(final String reqstr, final String avastr) {
            final int need = this.total - this.inventory - this.placed;
            if (this.inventory != -1 && need > 0) {
                return String.format("\u00a7c%s:%s", reqstr, getFormattedStackAmount(itemStack, need));
            } else {
                return String.format("\u00a7a%s", avastr);
            }
        }

        private static String getFormattedStackAmount(final ItemStack itemStack, final int amount) {
            final int stackSize = itemStack.getMaxStackSize();
            if (amount < stackSize) {
                return String.format("%d", amount);
            } else {
                final int amountstack = amount / stackSize;
                final int amountremainder = amount % stackSize;
                return String.format("%d(%d:%d)", amount, amountstack, amountremainder);
            }
        }
    }
}

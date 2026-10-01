package com.github.lunatrius.schematica.client.printer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.IFluidBlock;

import com.github.lunatrius.core.util.vector.Vector3i;
import com.github.lunatrius.schematica.client.printer.registry.PlacementData;
import com.github.lunatrius.schematica.client.printer.registry.PlacementRegistry;
import com.github.lunatrius.schematica.client.util.BlockToItemStack;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.compat.microblock.IMicroblocks;
import com.github.lunatrius.schematica.compat.microblock.MicroblockClick;
import com.github.lunatrius.schematica.compat.microblock.PartSpec;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Constants;
import com.github.lunatrius.schematica.reference.Reference;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;

public class SchematicPrinter {

    public static final SchematicPrinter INSTANCE = new SchematicPrinter();
    public static final FMLControlledNamespacedRegistry<Block> BLOCK_REGISTRY = GameData.getBlockRegistry();

    private final Minecraft minecraft = Minecraft.getMinecraft();

    private boolean isEnabled;
    private boolean isPrinting;

    private SchematicWorld schematic = null;
    private byte[][][] timeout = null;

    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public boolean togglePrinting() {
        this.isPrinting = !this.isPrinting;
        return this.isPrinting;
    }

    public boolean isPrinting() {
        return this.isPrinting;
    }

    public void setPrinting(boolean isPrinting) {
        this.isPrinting = isPrinting;
    }

    public SchematicWorld getSchematic() {
        return this.schematic;
    }

    public void setSchematic(SchematicWorld schematic) {
        this.isPrinting = false;
        this.schematic = schematic;
        refresh();
    }

    public void refresh() {
        if (this.schematic != null) {
            this.timeout = new byte[this.schematic.getWidth()][this.schematic.getHeight()][this.schematic.getLength()];
        } else {
            this.timeout = null;
        }
    }

    public boolean print() {
        final EntityClientPlayerMP player = this.minecraft.thePlayer;
        final World world = this.minecraft.theWorld;

        syncSneaking(player, true);

        final Vector3i trans = ClientProxy.playerPosition.clone()
            .sub(this.schematic.position.x, this.schematic.position.y, this.schematic.position.z)
            .toVector3i();
        final int minX = Math.max(0, trans.x - 3);
        final int maxX = Math.min(this.schematic.getWidth(), trans.x + 4);
        final int minY = Math.max(0, trans.y - 3);
        final int maxY = Math.min(this.schematic.getHeight(), trans.y + 4);
        final int minZ = Math.max(0, trans.z - 3);
        final int maxZ = Math.min(this.schematic.getLength(), trans.z + 4);

        final int slot = player.inventory.currentItem;
        final boolean isSneaking = player.isSneaking();

        final boolean isRenderingLayer = this.schematic.isRenderingLayer;
        final int renderingLayer = this.schematic.renderingLayer;
        for (int y = minY; y < maxY; y++) {
            if (isRenderingLayer && y != renderingLayer) {
                continue;
            }

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    try {
                        if (placeBlock(world, player, x, y, z)) {
                            player.inventory.currentItem = slot;
                            syncSneaking(player, isSneaking);
                            return true;
                        }
                    } catch (Exception e) {
                        Reference.logger.error("Could not place block!", e);
                        player.inventory.currentItem = slot;
                        syncSneaking(player, isSneaking);
                        return false;
                    }
                }
            }
        }

        player.inventory.currentItem = slot;
        syncSneaking(player, isSneaking);
        return true;
    }

    private boolean placeBlock(World world, EntityPlayer player, int x, int y, int z) {
        if (this.timeout[x][y][z] > 0) {
            this.timeout[x][y][z] -= ConfigurationHandler.placeDelay;
            return false;
        }

        final int wx = this.schematic.position.x + x;
        final int wy = this.schematic.position.y + y;
        final int wz = this.schematic.position.z + z;

        final Block block = this.schematic.getBlock(x, y, z);
        final Block realBlock = world.getBlock(wx, wy, wz);
        final int metadata = this.schematic.getBlockMetadata(x, y, z);
        final int realMetadata = world.getBlockMetadata(wx, wy, wz);

        if (ClientProxy.microblocks.isMultipart(block)) {
            return placeMicroblock(world, (EntityClientPlayerMP) player, x, y, z, wx, wy, wz, realBlock);
        }

        if (block == realBlock && metadata == realMetadata) {
            return false;
        }

        if (ConfigurationHandler.destroyBlocks && !world.isAirBlock(wx, wy, wz)
            && this.minecraft.playerController.isInCreativeMode()) {
            this.minecraft.playerController.clickBlock(wx, wy, wz, 0);

            this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;

            return !ConfigurationHandler.destroyInstantly;
        }

        if (this.schematic.isAirBlock(x, y, z)) {
            return false;
        }

        if (!realBlock.isReplaceable(world, wx, wy, wz)) {
            return false;
        }

        final ItemStack itemStack = BlockToItemStack.getItemStack(player, block, this.schematic, x, y, z);
        if (itemStack == null || itemStack.getItem() == null) {
            Reference.logger.debug("{} is missing a mapping!", BLOCK_REGISTRY.getNameForObject(block));
            return false;
        }

        if (placeBlock(world, player, wx, wy, wz, block, metadata, itemStack)) {
            this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;

            if (!ConfigurationHandler.placeInstantly) {
                return true;
            }
        }

        return false;
    }

    /**
     * Places one missing microblock (Forge Multipart strip, cover, corner...) of the schematic's multipart block at
     * (x, y, z). Forge Multipart chooses the part's slot from where the player's look ray meets the clicked face, so
     * this turns the player to look at a point on a neighbouring block's face, clicks it, and turns back.
     */
    private boolean placeMicroblock(World world, EntityClientPlayerMP player, int x, int y, int z, int wx, int wy,
        int wz, Block realBlock) {
        final IMicroblocks microblocks = ClientProxy.microblocks;
        if (!microblocks.isMultipart(realBlock) && !realBlock.isReplaceable(world, wx, wy, wz)) {
            return false;
        }

        final double reach = this.minecraft.playerController.getBlockReachDistance();
        for (PartSpec part : microblocks.partsOf(this.schematic.getTileEntity(x, y, z))) {
            if (microblocks.isPlaced(world, wx, wy, wz, part)) {
                continue;
            }
            final ItemStack itemStack = microblocks.itemFor(part);
            if (itemStack == null || (!this.minecraft.playerController.isInCreativeMode()
                && getInventorySlotWithItem(player.inventory, itemStack, true) < 0)) {
                continue;
            }
            final MicroblockClick click = microblocks.planClick(world, player, wx, wy, wz, part, reach);
            if (click == null) {
                Reference.logger.debug("No reachable face places {} at {} {} {}", part, wx, wy, wz);
                continue;
            }
            this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;
            if (!swapToItem(player.inventory, itemStack, true, true)) {
                return false;
            }

            final float yaw = player.rotationYaw, pitch = player.rotationPitch;
            player.sendQueue
                .addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(click.yaw, click.pitch, player.onGround));
            player.rotationYaw = click.yaw;
            player.rotationPitch = click.pitch;
            final boolean success = placeBlock(
                world,
                player,
                player.getCurrentEquippedItem(),
                click.x,
                click.y,
                click.z,
                click.side,
                click.hit);
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, player.onGround));

            final ItemStack held = player.getCurrentEquippedItem();
            if (held != null && held.stackSize == 0) {
                player.inventory.mainInventory[player.inventory.currentItem] = null;
            }
            return success && !ConfigurationHandler.placeInstantly;
        }
        // Nothing placeable here right now (no item, or no reachable face); try again after the timeout.
        this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;
        return false;
    }

    private boolean isSolid(World world, int x, int y, int z, ForgeDirection side) {
        x += side.offsetX;
        y += side.offsetY;
        z += side.offsetZ;

        Block block = world.getBlock(x, y, z);

        if (block == null) {
            return false;
        }

        if (block.isAir(world, x, y, z)) {
            return false;
        }

        if (block instanceof BlockFluidBase) {
            return false;
        }

        if (block.isReplaceable(world, x, y, z)) {
            return false;
        }

        return true;
    }

    private ForgeDirection[] getSolidSides(World world, int x, int y, int z) {
        List<ForgeDirection> list = new ArrayList<>();

        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (isSolid(world, x, y, z, side)) {
                list.add(side);
            }
        }

        ForgeDirection[] sides = new ForgeDirection[list.size()];
        return list.toArray(sides);
    }

    private boolean placeBlock(World world, EntityPlayer player, int x, int y, int z, Block block, int metadata,
        ItemStack itemStack) {
        if (isBlacklisted(block, itemStack)) {
            return false;
        }

        PlacementData data = PlacementRegistry.INSTANCE.getPlacementData(block, itemStack);

        if (!isValidOrientation(player, x, y, z, data, metadata)) {
            return false;
        }

        ForgeDirection[] solidSides = getSolidSides(world, x, y, z);
        ForgeDirection direction = ForgeDirection.UNKNOWN;
        float offsetY = 0.0f;
        int extraClicks = 0;

        if (solidSides.length > 0) {
            if (data != null) {
                ForgeDirection[] validDirections = data.getValidDirections(solidSides, metadata);
                if (validDirections.length > 0) {
                    direction = validDirections[0];
                }

                offsetY = data.getOffsetFromMetadata(metadata);
                extraClicks = data.getExtraClicks(block, metadata);
            } else {
                direction = solidSides[0];
            }

            if (!swapToItem(player.inventory, itemStack)) {
                return false;
            }
        }

        if (direction != ForgeDirection.UNKNOWN || !ConfigurationHandler.placeAdjacent) {
            return placeBlock(world, player, x, y, z, direction, 0.0f, offsetY, 0.0f, extraClicks);
        }

        return false;
    }

    private boolean isBlacklisted(Block block, ItemStack itemStack) {
        if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
            return true;
        }

        if (itemStack.getItem() instanceof ItemBucket) {
            return true;
        }

        if (itemStack.getItem() == Items.sign) {
            return true;
        }

        if (ClientProxy.lotrProxy.isBlackListed(block, itemStack)) {
            return true;
        }

        return false;
    }

    private boolean isValidOrientation(EntityPlayer player, int x, int y, int z, PlacementData data, int metadata) {
        if (data != null) {
            switch (data.type) {
                case BLOCK: {
                    return true;
                }

                case PLAYER: {
                    Integer integer = data.mapping.get(ClientProxy.orientation);
                    if (integer != null) {
                        return integer == (metadata & data.maskMeta);
                    }
                    break;
                }

                case PISTON: {
                    Integer integer = data.mapping.get(ClientProxy.orientation);
                    if (integer != null) {
                        return BlockPistonBase.determineOrientation(null, x, y, z, player)
                            == BlockPistonBase.getPistonOrientation(metadata);
                    }
                    break;
                }
            }
            return false;
        }

        return true;
    }

    private boolean placeBlock(World world, EntityPlayer player, int x, int y, int z, ForgeDirection direction,
        float offsetX, float offsetY, float offsetZ, int extraClicks) {
        ItemStack itemStack = player.getCurrentEquippedItem();

        if (!this.minecraft.playerController.isInCreativeMode() && itemStack != null
            && itemStack.stackSize <= extraClicks) {
            return false;
        }

        x += direction.offsetX;
        y += direction.offsetY;
        z += direction.offsetZ;

        int side = direction.getOpposite()
            .ordinal();
        final Vec3 hitVec = Vec3.createVectorHelper(x + offsetX, y + offsetY, z + offsetZ);

        boolean success = placeBlock(world, player, itemStack, x, y, z, side, hitVec);
        for (int i = 0; success && i < extraClicks; i++) {
            success = placeBlock(world, player, itemStack, x, y, z, side, hitVec);
        }

        if (itemStack != null && itemStack.stackSize == 0 && success) {
            player.inventory.mainInventory[player.inventory.currentItem] = null;
        }

        return success;
    }

    private boolean placeBlock(World world, EntityPlayer player, ItemStack itemStack, int x, int y, int z, int side,
        Vec3 hitVec) {
        boolean success = !ForgeEventFactory.onPlayerInteract(player, Action.RIGHT_CLICK_BLOCK, x, y, z, side, world)
            .isCanceled();
        if (success) {
            success = this.minecraft.playerController
                .onPlayerRightClick(player, world, itemStack, x, y, z, side, hitVec);
            if (success) {
                player.swingItem();
            }
        }

        return success;
    }

    private void syncSneaking(EntityClientPlayerMP player, boolean isSneaking) {
        player.setSneaking(isSneaking);
        player.sendQueue.addToSendQueue(new C0BPacketEntityAction(player, isSneaking ? 1 : 2));
    }

    private boolean swapToItem(InventoryPlayer inventory, ItemStack itemStack) {
        return swapToItem(inventory, itemStack, true, false);
    }

    /** matchNbt: the item's NBT must match too (microblock items differ only by their "mat" tag). */
    private boolean swapToItem(InventoryPlayer inventory, ItemStack itemStack, boolean swapSlots, boolean matchNbt) {
        int slot = getInventorySlotWithItem(inventory, itemStack, matchNbt);

        if (this.minecraft.playerController.isInCreativeMode()
            && (slot < Constants.Inventory.InventoryOffset.HOTBAR
                || slot >= Constants.Inventory.InventoryOffset.HOTBAR + Constants.Inventory.Size.HOTBAR)
            && !ConfigurationHandler.swapSlotsQueue.isEmpty()) {
            inventory.currentItem = getNextSlot();
            inventory.setInventorySlotContents(inventory.currentItem, itemStack.copy());
            this.minecraft.playerController.sendSlotPacket(
                inventory.getStackInSlot(inventory.currentItem),
                Constants.Inventory.SlotOffset.HOTBAR + inventory.currentItem);
            return true;
        }

        if (slot >= Constants.Inventory.InventoryOffset.HOTBAR
            && slot < Constants.Inventory.InventoryOffset.HOTBAR + Constants.Inventory.Size.HOTBAR) {
            inventory.currentItem = slot;
            return true;
        } else if (swapSlots && slot >= Constants.Inventory.InventoryOffset.INVENTORY
            && slot < Constants.Inventory.InventoryOffset.INVENTORY + Constants.Inventory.Size.INVENTORY) {
                if (swapSlots(inventory, slot)) {
                    return swapToItem(inventory, itemStack, false, matchNbt);
                }
            }
        return false;
    }

    private int getInventorySlotWithItem(final InventoryPlayer inventory, final ItemStack itemStack,
        final boolean matchNbt) {
        for (int i = 0; i < inventory.mainInventory.length; i++) {
            if (inventory.mainInventory[i] != null && inventory.mainInventory[i].isItemEqual(itemStack)
                && (!matchNbt || ItemStack.areItemStackTagsEqual(inventory.mainInventory[i], itemStack))) {
                return i;
            }
        }
        return -1;
    }

    private boolean swapSlots(InventoryPlayer inventory, int from) {
        if (!ConfigurationHandler.swapSlotsQueue.isEmpty()) {
            int slot = getNextSlot();

            swapSlots(from, slot);
            return true;
        }

        return false;
    }

    private int getNextSlot() {
        int slot = ConfigurationHandler.swapSlotsQueue.poll() % Constants.Inventory.Size.HOTBAR;
        ConfigurationHandler.swapSlotsQueue.offer(slot);
        return slot;
    }

    private boolean swapSlots(final int from, final int to) {
        return this.minecraft.playerController
            .windowClick(this.minecraft.thePlayer.inventoryContainer.windowId, from, to, 2, this.minecraft.thePlayer)
            == null;
    }
}

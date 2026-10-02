package com.github.lunatrius.schematica.compat.architecture;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.BlockFluidBase;

import gcewing.architecture.ArchitectureCraft;
import gcewing.architecture.common.shape.Shape;
import gcewing.architecture.common.shape.Window;
import gcewing.architecture.common.tile.TileShape;
import gcewing.architecture.compat.BlockCompatUtils;
import gcewing.architecture.compat.BlockPos;
import gcewing.architecture.compat.Vec3i;
import gcewing.architecture.compat.Vector3;
import gcewing.architecture.util.Utils;

/**
 * ArchitectureCraft 1.12.17 (GTNH) printing. Loaded only when ArchitectureCraft is installed.
 *
 * Placement (ShapeItem.placeBlockAt) is server-side and uses the click packet's hit point, which the packet carries
 * in sixteenths. The new shape's side and turn come from Shape.orientOnPlacement: copied from a shape neighbour
 * unless the player sneaks, otherwise from the clicked face and where on it the click lands. So the printer sneaks,
 * tries every face and sixteenth-grid point against ArchitectureCraft's own orientation code, and clicks the one that
 * gives the wanted side, turn and banister offset, preferring points whose neighbours give the same result. What a
 * click cannot reach is fixed afterwards: the hammer turns the side (sneaking) or the turn and offset (not sneaking),
 * a right-click with a glass pane or cladding adds the second material, and the chisel toggles connections.
 */
public class ArchitecturePresent implements IArchitecture {

    /** Chisel and edge-zone clicks land this far from the centre (sideZoneSize is 1/4, windows 1/8). */
    private static final double ZONE = 7 / 16d;

    @Override
    public boolean isShape(Block block) {
        return block == ArchitectureCraft.content.blockShape || block == ArchitectureCraft.content.blockShapeSE;
    }

    @Override
    public ShapeSpec specOf(Block block, TileEntity tileEntity) {
        if (!isShape(block) || !(tileEntity instanceof TileShape)) {
            return null;
        }
        final NBTTagCompound tag = new NBTTagCompound();
        tileEntity.writeToNBT(tag);
        return new ShapeSpec(block == ArchitectureCraft.content.blockShapeSE, tag);
    }

    @Override
    public ItemStack itemFor(ShapeSpec spec) {
        final Block block = spec.glow ? ArchitectureCraft.content.blockShapeSE : ArchitectureCraft.content.blockShape;
        final ItemStack stack = new ItemStack(Item.getItemFromBlock(block), 1, 0);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Shape", spec.shape);
        tag.setString("BaseName", spec.baseName);
        tag.setInteger("BaseData", spec.baseData);
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    public ItemStack secondaryItemFor(ShapeSpec spec) {
        if (spec.name2 == null) {
            return null;
        }
        final Block block = Block.getBlockFromName(spec.name2);
        if (block == null) {
            return null;
        }
        if (Shape.forId(spec.shape).kind instanceof Window) {
            final Item item = Item.getItemFromBlock(block);
            return item == null ? null : new ItemStack(item, 1, spec.data2);
        }
        return ArchitectureCraft.content.itemCladding.newStack(block, spec.data2, 1);
    }

    @Override
    public void withSneak(EntityClientPlayerMP player, boolean sneak, Runnable click) {
        // EntityPlayerSP.isSneaking() reads the sneak key, not the flag the server was sent; set it for the
        // client-side half of the click (block activation, ArchitectureCraft's own placement) and put it back.
        final boolean before = player.movementInput.sneak;
        player.movementInput.sneak = sneak;
        try {
            click.run();
        } finally {
            player.movementInput.sneak = before;
        }
    }

    @Override
    public ShapeAction nextAction(World world, EntityClientPlayerMP player, int x, int y, int z, ShapeSpec want,
        ShapeSpec have, Wanted wanted, StringBuilder why) {
        if (have == null) {
            return planPlacement(world, player, x, y, z, want, why);
        }
        if (!want.sameItem(have)) {
            why.append("a different shape is there: ")
                .append(have);
            return null;
        }
        if (have.side != want.side) {
            return fixUp(
                ShapeAction.Type.HAMMER,
                hammer(),
                false,
                true,
                x,
                y,
                z,
                "hammer (sneaking): side " + have.side + " -> " + ((have.side + 1) % 6));
        }
        if (!have.sameOrientation(want)) {
            // Hammer without sneaking: an offset flips sign; turn goes up by one whenever the offset is then >= 0.
            int turn = have.turn, off = have.offsetX;
            for (int i = 1; i <= 8; i++) {
                if (off != 0) off = (byte) -off;
                if (off >= 0) turn = (turn + 1) & 3;
                if (turn == want.turn && off == want.offsetX) {
                    return fixUp(
                        ShapeAction.Type.HAMMER,
                        hammer(),
                        false,
                        false,
                        x,
                        y,
                        z,
                        "hammer: turn " + have.turn
                            + " offset "
                            + have.offsetX
                            + " -> turn "
                            + want.turn
                            + " offset "
                            + want.offsetX
                            + " in "
                            + i);
                }
            }
            why.append("turn ")
                .append(want.turn)
                .append(" offset ")
                .append(want.offsetX)
                .append("/16 cannot be reached from turn ")
                .append(have.turn)
                .append(" offset ")
                .append(have.offsetX)
                .append("/16 with the hammer");
            return null;
        }
        if (!have.sameSecondary(want)) {
            if (have.name2 != null) {
                // The chisel in the centre takes the second material off (and drops it).
                return chisel(x, y, z, -1, "chisel the centre: take off " + have.name2 + "@" + have.data2);
            }
            final ItemStack item = secondaryItemFor(want);
            if (item == null) {
                why.append("no item for the second material ")
                    .append(want.name2);
                return null;
            }
            return new ShapeAction(
                ShapeAction.Type.SECONDARY,
                item,
                item.hasTagCompound(),
                false,
                x,
                y,
                z,
                1,
                Vec3.createVectorHelper(x + 8.5 / 16, y + 1, z + 8.5 / 16),
                "add " + want.name2 + "@" + want.data2);
        }
        final int diff = have.disconnected ^ want.disconnected;
        for (int d = 0; d < 6; d++) {
            if ((diff & (1 << d)) == 0) continue;
            // The chisel sets this connection and the neighbour shape's matching one to the same new state.
            final boolean off = (want.disconnected & (1 << d)) != 0;
            final ForgeDirection dir = ForgeDirection.getOrientation(d);
            final int nx = x + dir.offsetX, ny = y + dir.offsetY, nz = z + dir.offsetZ;
            final ShapeSpec nHave = specOf(world.getBlock(nx, ny, nz), world.getTileEntity(nx, ny, nz));
            final ShapeSpec nWant = wanted.at(nx, ny, nz);
            if (nHave == null && nWant != null) {
                why.append("connection ")
                    .append(dir)
                    .append(": waiting for the neighbour shape");
                continue;
            }
            if (nHave != null && nWant != null && ((nWant.disconnected & (1 << (d ^ 1))) != 0) != off) {
                why.append("connection ")
                    .append(dir)
                    .append(": the schematic has it one-sided, which the chisel cannot make");
                continue;
            }
            return chisel(x, y, z, d, "chisel: " + (off ? "disconnect " : "connect ") + dir);
        }
        if (diff != 0) {
            return null;
        }
        why.append("already matches");
        return null;
    }

    private static ItemStack hammer() {
        return new ItemStack(ArchitectureCraft.content.itemHammer);
    }

    private static ShapeAction fixUp(ShapeAction.Type type, ItemStack item, boolean matchNbt, boolean sneak, int x,
        int y, int z, String description) {
        // Click the shape's top face in the middle; the hammer ignores where.
        return new ShapeAction(
            type,
            item,
            matchNbt,
            sneak,
            x,
            y,
            z,
            1,
            Vec3.createVectorHelper(x + 8.5 / 16, y + 1, z + 8.5 / 16),
            description);
    }

    /** A chisel click on the shape: toward global direction `dir`'s edge zone, or the centre for dir -1. */
    private static ShapeAction chisel(int x, int y, int z, int dir, String description) {
        // Click the top face, or the north face for an up/down zone; the hit lies ZONE toward `dir` from the centre.
        final int face = dir == 0 || dir == 1 ? 2 : 1;
        final ForgeDirection f = ForgeDirection.getOrientation(face);
        double hx = 0.5 * f.offsetX, hy = 0.5 * f.offsetY, hz = 0.5 * f.offsetZ;
        if (dir >= 0) {
            final ForgeDirection d = ForgeDirection.getOrientation(dir);
            hx += ZONE * d.offsetX;
            hy += ZONE * d.offsetY;
            hz += ZONE * d.offsetZ;
        }
        return new ShapeAction(
            ShapeAction.Type.CHISEL,
            new ItemStack(ArchitectureCraft.content.itemChisel),
            false,
            true,
            x,
            y,
            z,
            face,
            Vec3.createVectorHelper(x + packet(0.5 + hx), y + packet(0.5 + hy), z + packet(0.5 + hz)),
            description);
    }

    /**
     * A block-relative hit coordinate that survives the click packet, which truncates to sixteenths: the face plane
     * (0 or 1) as is, anything else in the middle of its sixteenth so truncation gives exactly that sixteenth.
     */
    private static double packet(double v) {
        if (v <= 0 || v >= 1) return Math.max(0, Math.min(1, v));
        return Math.floor(v * 16 + 1e-6) / 16 + 1 / 32d;
    }

    private static boolean isSolid(World world, int x, int y, int z) {
        final Block block = world.getBlock(x, y, z);
        return block != null && !block.isAir(world, x, y, z)
            && !(block instanceof BlockFluidBase)
            && !(block instanceof BlockLiquid)
            && !block.isReplaceable(world, x, y, z);
    }

    private ShapeAction planPlacement(World world, EntityClientPlayerMP player, int x, int y, int z, ShapeSpec want,
        StringBuilder why) {
        final Shape shape = Shape.forId(want.shape);
        if (shape.id != want.shape) {
            why.append("unknown shape id ")
                .append(want.shape);
            return null;
        }
        final Block base = Block.getBlockFromName(want.baseName);
        if (base == null) {
            why.append("unknown material ")
                .append(want.baseName);
            return null;
        }

        // Quality of a click: 3 exact; 2 right side and offset size (the hammer fixes the turn); 1 right side; 0
        // placed.
        ShapeAction best = null;
        int bestQuality = -1, bestScore = -1;
        boolean anyFace = false;
        for (int s = 0; s < 6; s++) {
            final ForgeDirection fd = ForgeDirection.getOrientation(s);
            // ShapeItem.onItemUse puts the shape next to the clicked block on the clicked face's side.
            final int cx = x - fd.offsetX, cy = y - fd.offsetY, cz = z - fd.offsetZ;
            if (!isSolid(world, cx, cy, cz)) continue;
            anyFace = true;
            final Block clicked = world.getBlock(cx, cy, cz);
            // Not sneaking lets a shape neighbour hand its orientation over and opens GUIs; only try it against plain
            // blocks, for the orientations a sneaking click cannot give (upside down from the side).
            final boolean plain = !isShape(clicked) && world.getTileEntity(cx, cy, cz) == null;
            for (int pass = 0; pass < (plain ? 2 : 1); pass++) {
                final boolean sneak = pass == 0;
                final int[][] quality = new int[17][17];
                for (int a = 0; a <= 16; a++) {
                    for (int b = 0; b <= 16; b++) {
                        quality[a][b] = simulate(
                            world,
                            player,
                            shape,
                            base,
                            want.baseData,
                            cx,
                            cy,
                            cz,
                            s,
                            sneak,
                            a,
                            b,
                            want);
                    }
                }
                for (int a = 1; a <= 15; a++) {
                    for (int b = 1; b <= 15; b++) {
                        final int q = quality[a][b];
                        if (q < bestQuality) continue;
                        // Robustness: how many points around this one give the same quality.
                        int score = 0;
                        for (int da = -2; da <= 2; da++) {
                            for (int db = -2; db <= 2; db++) {
                                if (quality[Math.max(0, Math.min(16, a + da))][Math.max(0, Math.min(16, b + db))] == q)
                                    score++;
                            }
                        }
                        if (!sneak) score -= 1; // prefer sneaking clicks: nothing else reacts to them
                        if (q > bestQuality || score > bestScore) {
                            bestQuality = q;
                            bestScore = score;
                            final double[] h = hitOnFace(s, a, b);
                            best = new ShapeAction(
                                ShapeAction.Type.PLACE,
                                itemFor(want),
                                true,
                                sneak,
                                cx,
                                cy,
                                cz,
                                s,
                                Vec3.createVectorHelper(cx + packet(h[0]), cy + packet(h[1]), cz + packet(h[2])),
                                String.format(
                                    "place against %d %d %d face %d at %d/16 %d/16%s (%s)",
                                    cx,
                                    cy,
                                    cz,
                                    s,
                                    a,
                                    b,
                                    sneak ? " sneaking" : "",
                                    q == 3 ? "exact"
                                        : q == 2 ? "hammer fixes the turn"
                                            : q == 1 ? "offset differs" : "hammer fixes the side"));
                        }
                    }
                }
            }
        }
        if (best == null) {
            why.append(anyFace ? "no click possible" : "no solid block next to it to click yet");
        }
        return best;
    }

    /** Block-relative hit point on vanilla face `s` of a block, at sixteenths (a, b) across the face. */
    private static double[] hitOnFace(int s, int a, int b) {
        final double u = a / 16d, v = b / 16d;
        switch (s) {
            case 0:
                return new double[] { u, 0, v };
            case 1:
                return new double[] { u, 1, v };
            case 2:
                return new double[] { u, v, 0 };
            case 3:
                return new double[] { u, v, 1 };
            case 4:
                return new double[] { 0, v, u };
            default:
                return new double[] { 1, v, u };
        }
    }

    /** Runs ArchitectureCraft's placement orientation for this click and rates the result against `want`. */
    private int simulate(World world, EntityClientPlayerMP player, Shape shape, Block base, int baseData, int cx,
        int cy, int cz, int s, boolean sneak, int a, int b, ShapeSpec want) {
        final double[] h = hitOnFace(s, a, b);
        // ShapeItem.placeBlockAt: hit relative to the new block's centre, d = the clicked face's direction.
        final Vec3i d = Vector3.getDirectionVec(Utils.facings[s]);
        final Vector3 hit = new Vector3(h[0] - d.getX() - 0.5, h[1] - d.getY() - 0.5, h[2] - d.getZ() - 0.5);
        final TileShape te = new TileShape(shape, base, baseData);
        final BlockPos npos = new BlockPos(cx, cy, cz);
        final int[] quality = new int[1];
        withSneak(player, sneak, () -> {
            te.shape.orientOnPlacement(
                player,
                te,
                npos,
                BlockCompatUtils.getWorldBlockState(world, npos),
                world.getTileEntity(cx, cy, cz),
                Utils.facings[s],
                hit);
            final int off = (byte) (16 * te.getOffsetX());
            if (te.side != want.side) quality[0] = 0;
            else if (Math.abs(off) != Math.abs(want.offsetX)) quality[0] = 1;
            else if (te.turn != want.turn || off != want.offsetX) quality[0] = 2;
            else quality[0] = 3;
        });
        return quality[0];
    }
}

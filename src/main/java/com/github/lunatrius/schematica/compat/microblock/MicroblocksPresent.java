package com.github.lunatrius.schematica.compat.microblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import codechicken.lib.vec.BlockCoord;
import codechicken.microblock.AdditionPlacement;
import codechicken.microblock.CommonMicroClass;
import codechicken.microblock.ExecutablePlacement;
import codechicken.microblock.ItemMicroPart;
import codechicken.microblock.MicroMaterialRegistry$;
import codechicken.microblock.Microblock;
import codechicken.microblock.MicroblockPlacement$;
import codechicken.microblock.PlacementProperties;
import codechicken.multipart.BlockMultipart;
import codechicken.multipart.TileMultipart;
import codechicken.multipart.handler.MultipartSaveLoad;

/**
 * Forge Multipart (1.7.12) side of {@link IMicroblocks}. Only loaded when Forge Microblocks is installed.
 *
 * <p>
 * Placing a microblock: Forge Multipart ignores the hit position in the click packet and re-traces from the player's
 * eyes along their look direction (ItemMicroPart.onItemUse, RayTracer.retraceBlock), then picks the slot from where
 * that ray meets the clicked face. Clicking the outer face of a full block places into the space beside it. So the
 * printer looks at a chosen point on a neighbouring face (a full block's, or an empty space's) and clicks it. Points
 * are chosen by running Forge Multipart's own placement logic (MicroblockPlacement) on a grid over each candidate
 * face and aiming for the middle of the region that gives the part, so small differences between the client's and
 * the server's view of the player do not change the result.
 */
public class MicroblocksPresent implements IMicroblocks {

    /** Grid steps per block edge when searching a face for click points. */
    private static final int GRID = 32;
    /**
     * Eye-position error (blocks) a click should survive; beyond this, prefer clicking a real block over an empty
     * space.
     */
    private static final double TOLERANCE_GOOD = 0.1;

    @Override
    public boolean isMultipart(Block block) {
        return block instanceof BlockMultipart;
    }

    @Override
    public List<PartSpec> partsOf(TileEntity tileEntity) {
        NBTTagCompound tag = null;
        if (tileEntity instanceof MultipartSaveLoad.TileNBTContainer) {
            // What a schematic's "savedMultipart" tile entity loads as: the saved NBT, unconverted.
            tag = ((MultipartSaveLoad.TileNBTContainer) tileEntity).tag();
        } else if (tileEntity instanceof TileMultipart) {
            tag = new NBTTagCompound();
            tileEntity.writeToNBT(tag);
        }
        if (tag == null) {
            return Collections.emptyList();
        }

        final NBTTagList list = tag.getTagList("parts", Constants.NBT.TAG_COMPOUND);
        final List<PartSpec> parts = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound part = list.getCompoundTagAt(i);
            if (part.hasKey("material") && part.hasKey("shape") && classFor(part.getString("id")) != null) {
                parts.add(new PartSpec(part.getString("id"), part.getString("material"), part.getByte("shape")));
            }
        }
        return parts;
    }

    @Override
    public ItemStack itemFor(PartSpec part) {
        final CommonMicroClass microClass = classFor(part.id);
        if (microClass == null) {
            return null;
        }
        return ItemMicroPart.create(1, microClass.getClassId() << 8 | part.size(), part.material);
    }

    @Override
    public boolean isPlaced(World world, int x, int y, int z, PartSpec part) {
        final TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileMultipart)) {
            return false;
        }
        for (Object o : ((TileMultipart) tileEntity).jPartList()) {
            if (o instanceof Microblock && matches((Microblock) o, part)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<PartSpec> placedParts(World world, int x, int y, int z) {
        final TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileMultipart)) {
            return Collections.emptyList();
        }
        final List<PartSpec> parts = new ArrayList<>();
        for (Object o : ((TileMultipart) tileEntity).jPartList()) {
            if (o instanceof Microblock) {
                final Microblock microblock = (Microblock) o;
                parts.add(
                    new PartSpec(
                        microblock.getType(),
                        MicroMaterialRegistry$.MODULE$.materialName(microblock.getMaterial()),
                        microblock.shape()));
            }
        }
        return parts;
    }

    @Override
    public MicroblockClick planClick(World world, EntityPlayer player, int x, int y, int z, PartSpec part,
        double reach) {
        final CommonMicroClass microClass = classFor(part.id);
        if (microClass == null) {
            return null;
        }
        final int material = MicroMaterialRegistry$.MODULE$.materialID(part.material);
        if (material == MicroMaterialRegistry$.MODULE$.getMissingId()) {
            return null;
        }
        final PlacementProperties properties = microClass.placementProperties();
        final boolean checkMaterial = !player.capabilities.isCreativeMode;
        final Vec3 eye = serverEye(player);

        MicroblockClick best = null;
        double[] bestScore = null;

        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            // Click the face of the neighbour that points at the target space: a full block's face, or, with no
            // block there, the face of the empty space (the server ray-traces it as a full cube all the same).
            final int cx = x - direction.offsetX, cy = y - direction.offsetY, cz = z - direction.offsetZ;
            final int side = direction.ordinal();
            final boolean solid = isFullBlock(world, cx, cy, cz);
            if (!solid && !world.isAirBlock(cx, cy, cz) || !facesEye(eye, cx, cy, cz, direction)) {
                continue;
            }

            final boolean[][] ok = new boolean[GRID + 1][GRID + 1];
            for (int i = 1; i < GRID; i++) {
                for (int j = 1; j < GRID; j++) {
                    final Vec3 point = facePoint(cx, cy, cz, direction, i / (double) GRID, j / (double) GRID);
                    if (eye.distanceTo(point) > reach) {
                        continue;
                    }
                    final MovingObjectPosition hit = new MovingObjectPosition(cx, cy, cz, side, point);
                    ok[i][j] = placesExactly(
                        MicroblockPlacement$.MODULE$
                            .apply(player, hit, part.size(), material, checkMaterial, properties),
                        x,
                        y,
                        z,
                        part);
                }
            }

            for (int i = 1; i < GRID; i++) {
                for (int j = 1; j < GRID; j++) {
                    if (!ok[i][j]) {
                        continue;
                    }
                    // Aim where the click survives the most error in our idea of the server's eye position:
                    // deep inside the region that gives the part, and square-on, since a ray that skims the face
                    // moves its hit point far for a small change in eye height.
                    final int clearance = clearance(ok, i, j);
                    final Vec3 point = facePoint(cx, cy, cz, direction, i / (double) GRID, j / (double) GRID);
                    final double tolerance = (clearance + 0.5) / GRID * steepness(eye, point, direction);
                    final double[] score = { Math.min(tolerance, TOLERANCE_GOOD), solid ? 1 : 0, tolerance };
                    if (bestScore == null || compare(score, bestScore) > 0) {
                        bestScore = score;
                        best = lookAt(eye, cx, cy, cz, side, point);
                    }
                }
            }
        }
        return best;
    }

    @Override
    public String mismatch(World world, EntityPlayer player, int x, int y, int z, MicroblockClick click, PartSpec part,
        double reach) {
        final CommonMicroClass microClass = classFor(part.id);
        if (microClass == null) {
            return "unknown microblock type " + part.id;
        }
        final int material = MicroMaterialRegistry$.MODULE$.materialID(part.material);
        final Vec3 eye = serverEye(player);
        final Vec3 look = player.getLook(1.0F);
        final Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        final MovingObjectPosition hit = world.getBlock(click.x, click.y, click.z)
            .collisionRayTrace(world, click.x, click.y, click.z, eye, end);
        if (hit == null) {
            return "the look ray misses the clicked block";
        }
        final ExecutablePlacement placement = MicroblockPlacement$.MODULE$.apply(
            player,
            hit,
            part.size(),
            material,
            !player.capabilities.isCreativeMode,
            microClass.placementProperties());
        if (placesExactly(placement, x, y, z, part)) {
            return null;
        }
        final String at = String.format(
            "hit side %d at %.3f %.3f %.3f",
            hit.sideHit,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord);
        if (placement == null) {
            return at + " places nothing";
        }
        final Microblock placed = placement.part();
        return String.format(
            "%s places %s shape %d at %d %d %d",
            at,
            placed.getType(),
            placed.shape() & 0xff,
            placement.pos().x,
            placement.pos().y,
            placement.pos().z);
    }

    @Override
    public String describeEye(EntityPlayer player) {
        final Vec3 eye = serverEye(player);
        return String.format(
            "eye %.3f %.3f %.3f (client feet %.3f %.3f %.3f, sneaking %s)",
            eye.xCoord,
            eye.yCoord,
            eye.zCoord,
            player.posX,
            player.boundingBox.minY,
            player.posZ,
            player.isSneaking());
    }

    /** Largest r such that every grid point within r steps (Chebyshev) also gives the part. */
    private static int clearance(boolean[][] ok, int i, int j) {
        for (int r = 1; r <= GRID; r++) {
            for (int di = -r; di <= r; di++) {
                for (int dj = -r; dj <= r; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != r) {
                        continue;
                    }
                    final int a = i + di, b = j + dj;
                    if (a < 0 || a > GRID || b < 0 || b > GRID || !ok[a][b]) {
                        return r - 1;
                    }
                }
            }
        }
        return GRID;
    }

    /** How square-on the look ray meets the face (1 = perpendicular); off-angle rays amplify aim errors. */
    private static double steepness(Vec3 eye, Vec3 point, ForgeDirection direction) {
        final Vec3 ray = eye.subtract(point)
            .normalize();
        return Math
            .abs(ray.xCoord * direction.offsetX + ray.yCoord * direction.offsetY + ray.zCoord * direction.offsetZ);
    }

    private static int compare(double[] a, double[] b) {
        for (int k = 0; k < a.length; k++) {
            final int c = Double.compare(a[k], b[k]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static CommonMicroClass classFor(String id) {
        for (CommonMicroClass microClass : CommonMicroClass.classes()) {
            if (microClass != null && microClass.getName()
                .equals(id)) {
                return microClass;
            }
        }
        return null;
    }

    private static boolean matches(Microblock microblock, PartSpec part) {
        return part.id.equals(microblock.getType()) && (microblock.shape() & 0xff) == part.shape
            && part.material.equals(MicroMaterialRegistry$.MODULE$.materialName(microblock.getMaterial()));
    }

    private static boolean placesExactly(ExecutablePlacement placement, int x, int y, int z, PartSpec part) {
        if (!(placement instanceof AdditionPlacement)) {
            return false;
        }
        final BlockCoord pos = placement.pos();
        if (pos.x != x || pos.y != y || pos.z != z) {
            return false;
        }
        final Microblock placed = placement.part();
        return part.id.equals(placed.getType()) && (placed.shape() & 0xff) == part.shape;
    }

    /**
     * Where the server traces from: feet + 1.62, 0.08 lower while sneaking (the printer sneaks), at the position the
     * server last received from this client (oldPos*, sent each tick), not where the client has moved since.
     */
    private static Vec3 serverEye(EntityPlayer player) {
        final double sneak = player.isSneaking() ? 0.08 : 0;
        if (player instanceof EntityClientPlayerMP) {
            final EntityClientPlayerMP client = (EntityClientPlayerMP) player;
            return Vec3.createVectorHelper(client.oldPosX, client.oldMinY + 1.62 - sneak, client.oldPosZ);
        }
        return Vec3.createVectorHelper(player.posX, player.boundingBox.minY + 1.62 - sneak, player.posZ);
    }

    /** A plain full-cube block: its face is where the server's ray trace will land. */
    private static boolean isFullBlock(World world, int x, int y, int z) {
        final Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z) || block.isReplaceable(world, x, y, z) || block instanceof BlockMultipart) {
            return false;
        }
        block.setBlockBoundsBasedOnState(world, x, y, z);
        return block.getBlockBoundsMinX() == 0 && block.getBlockBoundsMinY() == 0
            && block.getBlockBoundsMinZ() == 0
            && block.getBlockBoundsMaxX() == 1
            && block.getBlockBoundsMaxY() == 1
            && block.getBlockBoundsMaxZ() == 1;
    }

    /** The eye must be outside the face's plane, so the ray enters the block through that face. */
    private static boolean facesEye(Vec3 eye, int x, int y, int z, ForgeDirection direction) {
        final double eps = 0.05;
        if (direction.offsetX != 0) {
            return direction.offsetX > 0 ? eye.xCoord > x + 1 + eps : eye.xCoord < x - eps;
        }
        if (direction.offsetY != 0) {
            return direction.offsetY > 0 ? eye.yCoord > y + 1 + eps : eye.yCoord < y - eps;
        }
        return direction.offsetZ > 0 ? eye.zCoord > z + 1 + eps : eye.zCoord < z - eps;
    }

    /** Point (u, v) on the face of block (x, y, z) that points along direction. */
    private static Vec3 facePoint(int x, int y, int z, ForgeDirection direction, double u, double v) {
        if (direction.offsetX != 0) {
            return Vec3.createVectorHelper(x + (direction.offsetX > 0 ? 1 : 0), y + u, z + v);
        }
        if (direction.offsetY != 0) {
            return Vec3.createVectorHelper(x + u, y + (direction.offsetY > 0 ? 1 : 0), z + v);
        }
        return Vec3.createVectorHelper(x + u, y + v, z + (direction.offsetZ > 0 ? 1 : 0));
    }

    /** Yaw and pitch for Entity.getLook to point from the eye at the point. */
    static MicroblockClick lookAt(Vec3 eye, int x, int y, int z, int side, Vec3 point) {
        final double dx = point.xCoord - eye.xCoord, dy = point.yCoord - eye.yCoord, dz = point.zCoord - eye.zCoord;
        final float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        final float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new MicroblockClick(x, y, z, side, point, yaw, pitch);
    }
}

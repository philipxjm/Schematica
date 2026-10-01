package com.github.lunatrius.schematica.compat.microblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /**
     * Least eye-position error a click must survive to be sent at all. Below this (typically the far edge of reach,
     * where only a region's near corner is in range) wait for the player to come closer.
     */
    private static final double TOLERANCE_MIN = 0.05;

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
    public MicroblockClick planClick(World world, EntityPlayer player, int x, int y, int z, PartSpec part, double reach,
        BusySpaces busy) {
        final FaceMasks masks = masks(world, player, x, y, z, part);
        if (masks == null) {
            return null;
        }
        final Vec3 eye = serverEye(player);

        MicroblockClick best = null;
        double[] bestScore = null;

        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            final int side = direction.ordinal();
            final boolean[][] mask = masks.ok[side];
            final int cx = x - direction.offsetX, cy = y - direction.offsetY, cz = z - direction.offsetZ;
            if (mask == null || !facesEye(eye, cx, cy, cz, direction)
                || !masks.solid[side] && busy.isBusy(cx, cy, cz)) {
                continue;
            }

            final boolean[][] ok = new boolean[GRID + 1][GRID + 1];
            boolean any = false;
            for (int i = 1; i < GRID; i++) {
                for (int j = 1; j < GRID; j++) {
                    if (mask[i][j]
                        && eye.distanceTo(facePoint(cx, cy, cz, direction, i / (double) GRID, j / (double) GRID))
                            <= reach) {
                        ok[i][j] = true;
                        any = true;
                    }
                }
            }
            if (!any) {
                continue;
            }

            final int[][] clearance = clearance(ok);
            for (int i = 1; i < GRID; i++) {
                for (int j = 1; j < GRID; j++) {
                    if (!ok[i][j]) {
                        continue;
                    }
                    // Aim where the click survives the most error in our idea of the server's eye position:
                    // deep inside the region that gives the part, and square-on, since a ray that skims the face
                    // moves its hit point far for a small change in eye height.
                    final Vec3 point = facePoint(cx, cy, cz, direction, i / (double) GRID, j / (double) GRID);
                    final double tolerance = (clearance[i][j] + 0.5) / GRID * steepness(eye, point, direction);
                    final double[] score = { Math.min(tolerance, TOLERANCE_GOOD), masks.solid[side] ? 1 : 0,
                        tolerance };
                    if (bestScore == null || compare(score, bestScore) > 0) {
                        bestScore = score;
                        best = lookAt(eye, cx, cy, cz, side, point);
                        best.tolerance = tolerance;
                    }
                }
            }
        }
        return best != null && best.tolerance >= TOLERANCE_MIN ? best : null;
    }

    /**
     * For each face around the target, which grid points Forge Multipart turns into exactly this part (null for a
     * face that cannot be clicked). Independent of where the player stands, so it is cached until the target's
     * parts or the neighbouring blocks change; only reach and aim are worked out per tick.
     */
    private static final class FaceMasks {

        final boolean[][][] ok = new boolean[6][][];
        final boolean[] solid = new boolean[6];
    }

    private static final int MASK_CACHE_SIZE = 2048;
    private final Map<String, FaceMasks> maskCache = new LinkedHashMap<String, FaceMasks>(256, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FaceMasks> eldest) {
            return size() > MASK_CACHE_SIZE;
        }
    };

    private FaceMasks masks(World world, EntityPlayer player, int x, int y, int z, PartSpec part) {
        final CommonMicroClass microClass = classFor(part.id);
        if (microClass == null) {
            return null;
        }
        final int material = MicroMaterialRegistry$.MODULE$.materialID(part.material);
        if (material == MicroMaterialRegistry$.MODULE$.getMissingId()) {
            return null;
        }
        final boolean checkMaterial = !player.capabilities.isCreativeMode;

        final StringBuilder key = new StringBuilder();
        key.append(x)
            .append(',')
            .append(y)
            .append(',')
            .append(z)
            .append(' ')
            .append(part)
            .append(checkMaterial ? " s" : " c")
            .append(placedParts(world, x, y, z));
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            final int cx = x - direction.offsetX, cy = y - direction.offsetY, cz = z - direction.offsetZ;
            key.append(' ')
                .append(Block.getIdFromBlock(world.getBlock(cx, cy, cz)))
                .append(':')
                .append(world.getBlockMetadata(cx, cy, cz));
        }
        final String cacheKey = key.toString();
        FaceMasks masks = this.maskCache.get(cacheKey);
        if (masks != null) {
            return masks;
        }

        masks = new FaceMasks();
        final PlacementProperties properties = microClass.placementProperties();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            // Click the face of the neighbour that points at the target space: a full block's face, or, with no
            // block there, the face of the empty space (the server ray-traces it as a full cube all the same).
            final int cx = x - direction.offsetX, cy = y - direction.offsetY, cz = z - direction.offsetZ;
            final int side = direction.ordinal();
            final boolean solid = isFullBlock(world, cx, cy, cz);
            if (!solid && !world.isAirBlock(cx, cy, cz)) {
                continue;
            }
            final boolean[][] mask = new boolean[GRID + 1][GRID + 1];
            for (int i = 1; i < GRID; i++) {
                for (int j = 1; j < GRID; j++) {
                    final Vec3 point = facePoint(cx, cy, cz, direction, i / (double) GRID, j / (double) GRID);
                    final MovingObjectPosition hit = new MovingObjectPosition(cx, cy, cz, side, point);
                    mask[i][j] = placesExactly(
                        MicroblockPlacement$.MODULE$
                            .apply(player, hit, part.size(), material, checkMaterial, properties),
                        x,
                        y,
                        z,
                        part);
                }
            }
            masks.ok[side] = mask;
            masks.solid[side] = solid;
        }
        this.maskCache.put(cacheKey, masks);
        return masks;
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
            "eye %.3f %.3f %.3f (client feet %.3f %.3f %.3f, sneak flag %s, sneak key %s)",
            eye.xCoord,
            eye.yCoord,
            eye.zCoord,
            player.posX,
            player.boundingBox.minY,
            player.posZ,
            sneakFlag(player),
            player.isSneaking());
    }

    /**
     * Per grid point, the largest r such that every point within r steps (Chebyshev) is also ok: a two-pass
     * chessboard distance transform to the nearest not-ok point (the grid border counts as not ok), minus one.
     */
    private static int[][] clearance(boolean[][] ok) {
        final int n = GRID + 1, far = 4 * n;
        final int[][] d = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!ok[i][j]) {
                    d[i][j] = 0;
                    continue;
                }
                int v = far;
                if (i > 0) {
                    v = Math.min(v, d[i - 1][j] + 1);
                    if (j > 0) v = Math.min(v, d[i - 1][j - 1] + 1);
                    if (j < n - 1) v = Math.min(v, d[i - 1][j + 1] + 1);
                }
                if (j > 0) v = Math.min(v, d[i][j - 1] + 1);
                d[i][j] = v;
            }
        }
        for (int i = n - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (!ok[i][j]) {
                    continue;
                }
                int v = d[i][j];
                if (i < n - 1) {
                    v = Math.min(v, d[i + 1][j] + 1);
                    if (j > 0) v = Math.min(v, d[i + 1][j - 1] + 1);
                    if (j < n - 1) v = Math.min(v, d[i + 1][j + 1] + 1);
                }
                if (j < n - 1) v = Math.min(v, d[i][j + 1] + 1);
                d[i][j] = v;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = Math.max(0, d[i][j] - 1);
            }
        }
        return d;
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
        final double sneak = sneakFlag(player) ? 0.08 : 0;
        if (player instanceof EntityClientPlayerMP) {
            final EntityClientPlayerMP client = (EntityClientPlayerMP) player;
            return Vec3.createVectorHelper(client.oldPosX, client.oldMinY + 1.62 - sneak, client.oldPosZ);
        }
        return Vec3.createVectorHelper(player.posX, player.boundingBox.minY + 1.62 - sneak, player.posZ);
    }

    /**
     * Sneaking as the server sees it: the entity flag the printer sets and sends (C0B), not EntityPlayerSP.isSneaking,
     * which reports the sneak key and so says false while the printer sneaks.
     */
    private static boolean sneakFlag(EntityPlayer player) {
        return (player.getDataWatcher()
            .getWatchableObjectByte(0) & 1 << 1) != 0;
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

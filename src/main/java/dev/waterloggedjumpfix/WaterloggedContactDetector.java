package dev.waterloggedjumpfix;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;

/** Detects target blocks under every part of the player's lower hitbox. */
final class WaterloggedContactDetector {
    private static final double FOOT_SCAN_DEPTH = 0.08D;
    private static final double EDGE_EPSILON = 1.0E-7D;

    boolean isTouchingTarget(final Player player, final Location origin) {
        if (!player.isInWater()) {
            return false;
        }

        final World world = origin.getWorld();
        final double halfWidth = player.getWidth() / 2.0D;
        final int minX = blockCoordinate(origin.getX() - halfWidth + EDGE_EPSILON);
        final int maxX = blockCoordinate(origin.getX() + halfWidth - EDGE_EPSILON);
        final int minY = blockCoordinate(origin.getY() - FOOT_SCAN_DEPTH);
        final int maxY = blockCoordinate(origin.getY() + EDGE_EPSILON);
        final int minZ = blockCoordinate(origin.getZ() - halfWidth + EDGE_EPSILON);
        final int maxZ = blockCoordinate(origin.getZ() + halfWidth - EDGE_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isTarget(world.getBlockAt(x, y, z).getBlockData())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isTarget(final BlockData blockData) {
        return blockData instanceof Waterlogged waterlogged
            && waterlogged.isWaterlogged()
            && (blockData instanceof Slab || blockData instanceof Stairs);
    }

    private static int blockCoordinate(final double coordinate) {
        return (int) Math.floor(coordinate);
    }
}

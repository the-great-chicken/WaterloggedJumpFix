package dev.waterloggedjumpfix;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Tests a small movement in the direction represented by the current input. */
final class HorizontalCollisionProbe {
    private static final double PROBE_DISTANCE = 0.08D;

    boolean isMovingIntoCollision(
        final Player player,
        final Location origin,
        final float yaw,
        final Input input
    ) {
        final double forward = axis(input.isForward(), input.isBackward());
        final double sideways = axis(input.isRight(), input.isLeft());
        if (forward == 0.0D && sideways == 0.0D) {
            return false;
        }

        final double radians = Math.toRadians(yaw);
        final double x = -Math.sin(radians) * forward - Math.cos(radians) * sideways;
        final double z = Math.cos(radians) * forward - Math.sin(radians) * sideways;
        final double length = Math.hypot(x, z);
        final Location probeLocation = origin.clone().add(
            x / length * PROBE_DISTANCE,
            0.0D,
            z / length * PROBE_DISTANCE
        );

        return player.collidesAt(probeLocation);
    }

    private static double axis(final boolean positive, final boolean negative) {
        return (positive ? 1.0D : 0.0D) - (negative ? 1.0D : 0.0D);
    }
}

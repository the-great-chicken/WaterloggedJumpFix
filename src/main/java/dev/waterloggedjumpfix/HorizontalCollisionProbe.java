package dev.waterloggedjumpfix;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Tests a small movement in the direction represented by the current input. */
final class HorizontalCollisionProbe {
    private static final double SWEEP_SAMPLE_DISTANCE = 0.025D;

    boolean isMovingIntoCollision(
        final Player player,
        final Location origin,
        final MovementDirection direction,
        final double distance
    ) {
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return false;
        }

        if (!direction.isMoving()) {
            return false;
        }

        final Location probeLocation = origin.clone().add(
            direction.x() * distance,
            0.0D,
            direction.z() * distance
        );

        return player.collidesAt(probeLocation);
    }

    CollisionResult firstCollision(
        final Player player,
        final Location origin,
        final MovementDirection direction,
        final double distance
    ) {
        if (!Double.isFinite(distance)
            || distance <= 0.0D
            || !direction.isMoving()) {
            return CollisionResult.NONE;
        }

        final int samples = Math.max(
            1,
            (int) Math.ceil(distance / SWEEP_SAMPLE_DISTANCE)
        );
        for (int sample = 1; sample <= samples; sample++) {
            final double sampleDistance = distance * sample / samples;
            if (this.isMovingIntoCollision(
                player,
                origin,
                direction,
                sampleDistance
            )) {
                return new CollisionResult(true, sampleDistance);
            }
        }
        return CollisionResult.NONE;
    }

    static MovementDirection movementDirection(final float yaw, final Input input) {
        final double forward = axis(input.isForward(), input.isBackward());
        final double sideways = axis(input.isRight(), input.isLeft());
        if (forward == 0.0D && sideways == 0.0D) {
            return MovementDirection.NONE;
        }

        final double radians = Math.toRadians(yaw);
        final double x = -Math.sin(radians) * forward - Math.cos(radians) * sideways;
        final double z = Math.cos(radians) * forward - Math.sin(radians) * sideways;
        final double length = Math.hypot(x, z);
        return new MovementDirection(x / length, z / length);
    }

    private static double axis(final boolean positive, final boolean negative) {
        return (positive ? 1.0D : 0.0D) - (negative ? 1.0D : 0.0D);
    }

    record MovementDirection(double x, double z) {
        private static final MovementDirection NONE = new MovementDirection(
            0.0D,
            0.0D
        );

        boolean isMoving() {
            return Double.isFinite(this.x)
                && Double.isFinite(this.z)
                && (this.x != 0.0D || this.z != 0.0D);
        }
    }

    record CollisionResult(boolean collision, double distance) {
        private static final CollisionResult NONE = new CollisionResult(
            false,
            Double.NaN
        );
    }
}

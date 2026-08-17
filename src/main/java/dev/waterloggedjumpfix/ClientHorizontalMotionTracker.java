package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Estimates the horizontal velocity retained by the client between ticks. */
final class ClientHorizontalMotionTracker {
    private static final double MAX_HORIZONTAL_DISTANCE_SQUARED = 1.0D;

    private final Map<UUID, PositionSample> previousSamples = new HashMap<>();
    private final Map<UUID, HorizontalMotion> latestMotions = new HashMap<>();

    HorizontalMotion observe(final Player player) {
        final Location location = player.getLocation();
        return this.observe(
            player.getUniqueId(),
            location.getWorld().getUID(),
            location.getX(),
            location.getZ()
        );
    }

    HorizontalMotion observe(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z
    ) {
        final PositionSample current = new PositionSample(worldId, x, z);
        final PositionSample previous = this.previousSamples.put(playerId, current);
        if (previous == null || !previous.worldId().equals(worldId)) {
            return this.remember(playerId, HorizontalMotion.ZERO);
        }

        final double deltaX = x - previous.x();
        final double deltaZ = z - previous.z();
        final double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (!Double.isFinite(distanceSquared)
            || distanceSquared > MAX_HORIZONTAL_DISTANCE_SQUARED) {
            return this.remember(playerId, HorizontalMotion.ZERO);
        }

        return this.remember(playerId, new HorizontalMotion(deltaX, deltaZ));
    }

    HorizontalMotion latest(final UUID playerId) {
        return this.latestMotions.getOrDefault(playerId, HorizontalMotion.ZERO);
    }

    void forget(final UUID playerId) {
        this.previousSamples.remove(playerId);
        this.latestMotions.remove(playerId);
    }

    void clear() {
        this.previousSamples.clear();
        this.latestMotions.clear();
    }

    private HorizontalMotion remember(
        final UUID playerId,
        final HorizontalMotion motion
    ) {
        this.latestMotions.put(playerId, motion);
        return motion;
    }

    record HorizontalMotion(double x, double z) {
        private static final double OPPOSING_COMPONENT_EPSILON = 1.0E-5D;
        static final HorizontalMotion ZERO = new HorizontalMotion(0.0D, 0.0D);

        HorizontalMotion damped(final double damping) {
            return new HorizontalMotion(this.x * damping, this.z * damping);
        }

        double speed() {
            return Math.hypot(this.x, this.z);
        }

        HorizontalMotion withoutOpposingComponents(
            final HorizontalCollisionProbe.MovementDirection direction
        ) {
            final double adjustedX = this.x * direction.x()
                    < -OPPOSING_COMPONENT_EPSILON
                ? 0.0D
                : this.x;
            final double adjustedZ = this.z * direction.z()
                    < -OPPOSING_COMPONENT_EPSILON
                ? 0.0D
                : this.z;
            return new HorizontalMotion(adjustedX, adjustedZ);
        }
    }

    private record PositionSample(UUID worldId, double x, double z) {
    }
}

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
            return HorizontalMotion.ZERO;
        }

        final double deltaX = x - previous.x();
        final double deltaZ = z - previous.z();
        final double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (!Double.isFinite(distanceSquared)
            || distanceSquared > MAX_HORIZONTAL_DISTANCE_SQUARED) {
            return HorizontalMotion.ZERO;
        }

        return new HorizontalMotion(deltaX, deltaZ);
    }

    void forget(final UUID playerId) {
        this.previousSamples.remove(playerId);
    }

    void clear() {
        this.previousSamples.clear();
    }

    record HorizontalMotion(double x, double z) {
        static final HorizontalMotion ZERO = new HorizontalMotion(0.0D, 0.0D);

        HorizontalMotion damped(final double damping) {
            return new HorizontalMotion(this.x * damping, this.z * damping);
        }
    }

    private record PositionSample(UUID worldId, double x, double z) {
    }
}

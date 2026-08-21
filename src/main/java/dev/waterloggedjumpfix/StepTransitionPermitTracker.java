package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Coordinates one predicted step transition across subsequent client packets. */
final class StepTransitionPermitTracker {
    static final int MAX_ARMED_CLIENT_TICKS = 2;
    static final int MAX_ACTIVE_CLIENT_TICKS = 8;
    private static final double MAX_ACTIVATION_HORIZONTAL_DISTANCE_SQUARED =
        0.35D * 0.35D;
    private static final double MAX_ACTIVATION_Y_DRIFT = 0.15D;
    private static final double MIN_DIRECTION_DOT = 0.5D;
    private static final double REARM_MOVEMENT_DISTANCE_SQUARED = 0.1D * 0.1D;
    private static final double TARGET_LOWER_TOLERANCE = 0.03D;
    private static final double TARGET_UPPER_TOLERANCE = 0.15D;

    private final Map<UUID, Permit> permits = new HashMap<>();
    private final Map<UUID, BlockedAttempt> blockedAttempts = new HashMap<>();

    void advanceClientTick(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        if (permit != null) {
            permit.ageClientTicks++;
        }
    }

    void arm(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double y,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction,
        final ProjectedStepSimulator.StepResult step
    ) {
        this.permits.put(
            playerId,
            new Permit(
                worldId,
                x,
                y,
                z,
                direction,
                step.rise(),
                Phase.ARMED
            )
        );
    }

    boolean activate(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double y,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        final Permit permit = this.permits.get(playerId);
        if (permit == null || permit.phase != Phase.ARMED) {
            return false;
        }
        if (this.isArmedExpired(playerId)
            || !permit.worldId.equals(worldId)
            || !direction.isMoving()
            || permit.direction.dot(direction) < MIN_DIRECTION_DOT
            || horizontalDistanceSquared(permit.anchorX, permit.anchorZ, x, z)
                > MAX_ACTIVATION_HORIZONTAL_DISTANCE_SQUARED
            || Math.abs(y - permit.baselineY) > MAX_ACTIVATION_Y_DRIFT) {
            this.permits.remove(playerId);
            return false;
        }

        permit.phase = Phase.ACTIVE;
        permit.ageClientTicks = 0;
        return true;
    }

    boolean hasReachedTarget(
        final UUID playerId,
        final double currentY,
        final boolean supported
    ) {
        final Permit permit = this.permits.get(playerId);
        if (permit == null || !supported || !Double.isFinite(currentY)) {
            return false;
        }

        final double targetY = permit.baselineY + permit.rise;
        return currentY >= targetY - TARGET_LOWER_TOLERANCE
            && currentY <= targetY + TARGET_UPPER_TOLERANCE;
    }

    boolean isArmed(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null && permit.phase == Phase.ARMED;
    }

    boolean isActive(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null && permit.phase == Phase.ACTIVE;
    }

    boolean matchesWorld(final UUID playerId, final UUID worldId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null && permit.worldId.equals(worldId);
    }

    HorizontalCollisionProbe.MovementDirection direction(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit == null
            ? new HorizontalCollisionProbe.MovementDirection(0.0D, 0.0D)
            : permit.direction;
    }

    boolean isArmedExpired(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null
            && permit.phase == Phase.ARMED
            && permit.ageClientTicks > MAX_ARMED_CLIENT_TICKS;
    }

    boolean isActiveExpired(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null
            && permit.phase == Phase.ACTIVE
            && permit.ageClientTicks > MAX_ACTIVE_CLIENT_TICKS;
    }

    void block(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        this.permits.remove(playerId);
        if (!direction.isMoving()) {
            this.blockedAttempts.remove(playerId);
            return;
        }
        this.blockedAttempts.put(
            playerId,
            new BlockedAttempt(worldId, x, z, direction)
        );
    }

    boolean isRearmBlocked(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        final BlockedAttempt blocked = this.blockedAttempts.get(playerId);
        if (blocked == null) {
            return false;
        }
        if (!blocked.worldId.equals(worldId)
            || !direction.isMoving()
            || blocked.direction.dot(direction) < MIN_DIRECTION_DOT
            || horizontalDistanceSquared(blocked.x, blocked.z, x, z)
                > REARM_MOVEMENT_DISTANCE_SQUARED) {
            this.blockedAttempts.remove(playerId);
            return false;
        }
        return true;
    }

    void complete(final UUID playerId) {
        this.permits.remove(playerId);
        this.blockedAttempts.remove(playerId);
    }

    void forget(final UUID playerId) {
        this.permits.remove(playerId);
        this.blockedAttempts.remove(playerId);
    }

    void clear() {
        this.permits.clear();
        this.blockedAttempts.clear();
    }

    private static double horizontalDistanceSquared(
        final double firstX,
        final double firstZ,
        final double secondX,
        final double secondZ
    ) {
        final double deltaX = secondX - firstX;
        final double deltaZ = secondZ - firstZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    enum Phase {
        ARMED,
        ACTIVE
    }

    private static final class Permit {
        private final UUID worldId;
        private final double anchorX;
        private final double baselineY;
        private final double anchorZ;
        private final HorizontalCollisionProbe.MovementDirection direction;
        private final double rise;
        private Phase phase;
        private int ageClientTicks;

        private Permit(
            final UUID worldId,
            final double anchorX,
            final double baselineY,
            final double anchorZ,
            final HorizontalCollisionProbe.MovementDirection direction,
            final double rise,
            final Phase phase
        ) {
            this.worldId = worldId;
            this.anchorX = anchorX;
            this.baselineY = baselineY;
            this.anchorZ = anchorZ;
            this.direction = direction;
            this.rise = rise;
            this.phase = phase;
        }
    }

    private static final class BlockedAttempt {
        private final UUID worldId;
        private final double x;
        private final double z;
        private final HorizontalCollisionProbe.MovementDirection direction;

        private BlockedAttempt(
            final UUID worldId,
            final double x,
            final double z,
            final HorizontalCollisionProbe.MovementDirection direction
        ) {
            this.worldId = worldId;
            this.x = x;
            this.z = z;
            this.direction = direction;
        }
    }
}

package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Coordinates one predicted step transition across subsequent client packets. */
final class StepTransitionPermitTracker {
    static final int MAX_ARMED_CLIENT_TICKS = 2;
    private static final int FALLBACK_CLIENT_TICK = 1;
    private static final double MAX_ACTIVATION_HORIZONTAL_DISTANCE_SQUARED =
        0.35D * 0.35D;
    private static final double MAX_ACTIVATION_Y_ABOVE_TARGET = 0.03D;
    private static final double MAX_CROSSED_TARGET_Y_OFFSET = 0.08D;
    private static final double STEP_HEIGHT_EPSILON = 1.0E-5D;
    private static final double MAX_LANDING_HORIZONTAL_DISTANCE_SQUARED =
        0.2D * 0.2D;
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
        final ProjectedStepSimulator.StepResult step,
        final ClientHorizontalMotionTracker.HorizontalMotion retainedMotion,
        final ArmSource source
    ) {
        if (!direction.isMoving()
            || !step.stepable()
            || !areFinite(
                x,
                y,
                z,
                step.rise(),
                step.offsetX(),
                step.offsetZ(),
                retainedMotion.x(),
                retainedMotion.z()
            )
            || step.rise() <= 0.0D
            || horizontalDistanceSquared(
                0.0D,
                0.0D,
                step.offsetX(),
                step.offsetZ()
            ) > MAX_LANDING_HORIZONTAL_DISTANCE_SQUARED) {
            this.permits.remove(playerId);
            return;
        }

        this.permits.put(
            playerId,
            new Permit(
                worldId,
                x,
                y,
                z,
                x + step.offsetX(),
                y + step.rise(),
                z + step.offsetZ(),
                direction,
                retainedMotion,
                source
            )
        );
    }

    LandingAttempt consumeLanding(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double y,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double stepHeight
    ) {
        final Permit permit = this.permits.remove(playerId);
        if (permit == null) {
            return LandingAttempt.NONE;
        }

        return validateLanding(
            permit,
            worldId,
            x,
            y,
            z,
            direction,
            stepHeight
        );
    }

    LandingAttempt inspectLanding(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double y,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double stepHeight
    ) {
        final Permit permit = this.permits.get(playerId);
        if (permit == null) {
            return LandingAttempt.NONE;
        }

        return validateLanding(
            permit,
            worldId,
            x,
            y,
            z,
            direction,
            stepHeight
        );
    }

    private static LandingAttempt validateLanding(
        final Permit permit,
        final UUID worldId,
        final double x,
        final double y,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double stepHeight
    ) {

        final Landing landing = permit.landing();
        final double horizontalDrift = horizontalDistance(
            permit.anchorX,
            permit.anchorZ,
            x,
            z
        );
        final double verticalDrift = areFinite(y, permit.baselineY)
            ? y - permit.baselineY
            : Double.NaN;
        final double directionDot = direction.isMoving()
            ? permit.direction.dot(direction)
            : Double.NaN;
        if (permit.ageClientTicks > MAX_ARMED_CLIENT_TICKS) {
            return new LandingAttempt(
                LandingStatus.EXPIRED,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }
        if (!permit.worldId.equals(worldId)) {
            return new LandingAttempt(
                LandingStatus.WORLD_MISMATCH,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }
        if (!direction.isMoving()
            || directionDot < MIN_DIRECTION_DOT) {
            return new LandingAttempt(
                LandingStatus.DIRECTION_MISMATCH,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }
        if (!areFinite(x, y, z)
            || !Double.isFinite(horizontalDrift)
            || horizontalDrift * horizontalDrift
                > MAX_ACTIVATION_HORIZONTAL_DISTANCE_SQUARED
            || !Double.isFinite(verticalDrift)
            || !Double.isFinite(stepHeight)
            || stepHeight < 0.0D
            || y < permit.targetY - stepHeight - STEP_HEIGHT_EPSILON) {
            return new LandingAttempt(
                LandingStatus.ORIGIN_DRIFTED,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }
        if (y > permit.targetY + MAX_CROSSED_TARGET_Y_OFFSET) {
            return new LandingAttempt(
                LandingStatus.ABOVE_TARGET,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }
        if (y > permit.targetY + MAX_ACTIVATION_Y_ABOVE_TARGET) {
            return new LandingAttempt(
                LandingStatus.CROSSED_TARGET,
                landing,
                horizontalDrift,
                verticalDrift,
                directionDot
            );
        }

        return new LandingAttempt(
            LandingStatus.ACCEPTED,
            landing,
            horizontalDrift,
            verticalDrift,
            directionDot
        );
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

        return currentY >= permit.targetY - TARGET_LOWER_TOLERANCE
            && currentY <= permit.targetY + TARGET_UPPER_TOLERANCE;
    }

    boolean isArmed(final UUID playerId) {
        return this.permits.containsKey(playerId);
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

    Landing landing(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit == null ? null : permit.landing();
    }

    boolean isArmedExpired(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null
            && permit.ageClientTicks > MAX_ARMED_CLIENT_TICKS;
    }

    boolean isFallbackReady(final UUID playerId) {
        final Permit permit = this.permits.get(playerId);
        return permit != null
            && permit.ageClientTicks == FALLBACK_CLIENT_TICK;
    }

    void blockUnwantedJump(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        this.block(
            playerId,
            worldId,
            x,
            z,
            direction
        );
    }

    void blockExpired(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        this.block(
            playerId,
            worldId,
            x,
            z,
            direction
        );
    }

    void blockRejected(
        final UUID playerId,
        final UUID worldId,
        final double x,
        final double z,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        this.block(
            playerId,
            worldId,
            x,
            z,
            direction
        );
    }

    private void block(
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
            new BlockedAttempt(
                worldId,
                x,
                z,
                direction
            )
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
        if (!blocked.matchesContact(worldId, x, z, direction)) {
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

    private static boolean areFinite(final double... values) {
        for (final double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
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

    private static double horizontalDistance(
        final double firstX,
        final double firstZ,
        final double secondX,
        final double secondZ
    ) {
        if (!areFinite(firstX, firstZ, secondX, secondZ)) {
            return Double.NaN;
        }
        return Math.sqrt(horizontalDistanceSquared(
            firstX,
            firstZ,
            secondX,
            secondZ
        ));
    }

    enum ArmSource {
        MOTION,
        STABLE_GEOMETRY
    }

    enum BlockReason {
        UNWANTED_JUMP,
        PERMIT_EXPIRED,
        PERMIT_REJECTED
    }

    enum LandingStatus {
        NONE,
        ACCEPTED,
        EXPIRED,
        WORLD_MISMATCH,
        DIRECTION_MISMATCH,
        ORIGIN_DRIFTED,
        CROSSED_TARGET,
        ABOVE_TARGET
    }

    record Landing(
        UUID worldId,
        double anchorX,
        double baselineY,
        double anchorZ,
        double targetX,
        double targetY,
        double targetZ,
        HorizontalCollisionProbe.MovementDirection direction,
        ClientHorizontalMotionTracker.HorizontalMotion retainedMotion,
        ArmSource source,
        int ageClientTicks
    ) {
        double rise() {
            return this.targetY - this.baselineY;
        }
    }

    record LandingAttempt(
        LandingStatus status,
        Landing landing,
        double horizontalDrift,
        double verticalDrift,
        double directionDot
    ) {
        private static final LandingAttempt NONE = new LandingAttempt(
            LandingStatus.NONE,
            null,
            Double.NaN,
            Double.NaN,
            Double.NaN
        );

        boolean accepted() {
            return this.status == LandingStatus.ACCEPTED;
        }

        boolean acceptedForFallback() {
            return this.accepted()
                || this.status == LandingStatus.CROSSED_TARGET;
        }

        double targetVerticalOffset() {
            if (this.landing == null
                || !Double.isFinite(this.verticalDrift)) {
                return Double.NaN;
            }
            return this.landing.baselineY()
                + this.verticalDrift
                - this.landing.targetY();
        }

        boolean hadPermit() {
            return this.status != LandingStatus.NONE;
        }
    }

    private static final class Permit {
        private final UUID worldId;
        private final double anchorX;
        private final double baselineY;
        private final double anchorZ;
        private final double targetX;
        private final double targetY;
        private final double targetZ;
        private final HorizontalCollisionProbe.MovementDirection direction;
        private final ClientHorizontalMotionTracker.HorizontalMotion
            retainedMotion;
        private final ArmSource source;
        private int ageClientTicks;

        private Permit(
            final UUID worldId,
            final double anchorX,
            final double baselineY,
            final double anchorZ,
            final double targetX,
            final double targetY,
            final double targetZ,
            final HorizontalCollisionProbe.MovementDirection direction,
            final ClientHorizontalMotionTracker.HorizontalMotion retainedMotion,
            final ArmSource source
        ) {
            this.worldId = worldId;
            this.anchorX = anchorX;
            this.baselineY = baselineY;
            this.anchorZ = anchorZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.direction = direction;
            this.retainedMotion = retainedMotion;
            this.source = source;
        }

        private Landing landing() {
            return new Landing(
                this.worldId,
                this.anchorX,
                this.baselineY,
                this.anchorZ,
                this.targetX,
                this.targetY,
                this.targetZ,
                this.direction,
                this.retainedMotion,
                this.source,
                this.ageClientTicks
            );
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

        private boolean matchesContact(
            final UUID candidateWorldId,
            final double candidateX,
            final double candidateZ,
            final HorizontalCollisionProbe.MovementDirection candidateDirection
        ) {
            return this.worldId.equals(candidateWorldId)
                && candidateDirection.isMoving()
                && this.direction.dot(candidateDirection) >= MIN_DIRECTION_DOT
                && horizontalDistanceSquared(
                    this.x,
                    this.z,
                    candidateX,
                    candidateZ
                ) <= REARM_MOVEMENT_DISTANCE_SQUARED;
        }
    }
}

package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Confirms that a stationary prediction repeatedly identifies the same step. */
final class StableStepCandidateTracker {
    static final int REQUIRED_CONSECUTIVE_OBSERVATIONS = 2;
    private static final double MAX_TARGET_HORIZONTAL_DRIFT_SQUARED =
        0.075D * 0.075D;
    private static final double MAX_TARGET_VERTICAL_DRIFT = 0.075D;
    private static final double MIN_DIRECTION_DOT = 0.5D;

    private final Map<UUID, Candidate> candidates = new HashMap<>();

    int observe(
        final UUID playerId,
        final UUID worldId,
        final double originX,
        final double originY,
        final double originZ,
        final HorizontalCollisionProbe.MovementDirection direction,
        final ProjectedStepSimulator.StepResult step
    ) {
        if (!direction.isMoving()
            || !step.stepable()
            || !areFinite(
                originX,
                originY,
                originZ,
                step.rise(),
                step.offsetX(),
                step.offsetZ()
            )
            || step.rise() <= 0.0D) {
            this.forget(playerId);
            return 0;
        }

        final double targetX = originX + step.offsetX();
        final double targetY = originY + step.rise();
        final double targetZ = originZ + step.offsetZ();
        final Candidate current = this.candidates.get(playerId);
        final int observations;
        if (current != null
            && current.matches(
                worldId,
                targetX,
                targetY,
                targetZ,
                direction
            )) {
            observations = Math.min(
                REQUIRED_CONSECUTIVE_OBSERVATIONS,
                current.observations + 1
            );
        } else {
            observations = 1;
        }

        this.candidates.put(
            playerId,
            new Candidate(
                worldId,
                targetX,
                targetY,
                targetZ,
                direction,
                observations
            )
        );
        return observations;
    }

    void forget(final UUID playerId) {
        this.candidates.remove(playerId);
    }

    void clear() {
        this.candidates.clear();
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

    private record Candidate(
        UUID worldId,
        double targetX,
        double targetY,
        double targetZ,
        HorizontalCollisionProbe.MovementDirection direction,
        int observations
    ) {
        private boolean matches(
            final UUID candidateWorldId,
            final double candidateTargetX,
            final double candidateTargetY,
            final double candidateTargetZ,
            final HorizontalCollisionProbe.MovementDirection candidateDirection
        ) {
            return this.worldId.equals(candidateWorldId)
                && candidateDirection.isMoving()
                && this.direction.dot(candidateDirection) >= MIN_DIRECTION_DOT
                && Math.abs(candidateTargetY - this.targetY)
                    <= MAX_TARGET_VERTICAL_DRIFT
                && horizontalDistanceSquared(
                    this.targetX,
                    this.targetZ,
                    candidateTargetX,
                    candidateTargetZ
                ) <= MAX_TARGET_HORIZONTAL_DRIFT_SQUARED;
        }
    }
}

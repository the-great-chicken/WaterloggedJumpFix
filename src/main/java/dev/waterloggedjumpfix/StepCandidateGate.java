package dev.waterloggedjumpfix;

/** Requires either horizontal progress or stable geometry at confirmed contact. */
final class StepCandidateGate {
    private static final double MIN_HORIZONTAL_PROGRESS = 0.02D;
    private static final double MIN_TARGET_DISTANCE = 1.0E-5D;
    private static final double MIN_ALIGNMENT_COSINE = 0.5D;
    private static final double PROJECTED_REACH_MULTIPLIER = 1.25D;
    private static final double PROJECTED_REACH_PADDING = 0.025D;
    private static final double MAX_PROJECTED_RELEASE_DISTANCE = 0.2D;

    double projectedProbeDistance(final double requestedDistance) {
        if (!Double.isFinite(requestedDistance) || requestedDistance <= 0.0D) {
            return 0.0D;
        }
        return Math.min(requestedDistance, MAX_PROJECTED_RELEASE_DISTANCE);
    }

    boolean trustsVanillaStep(
        final boolean confirmedSuppression,
        final ClientHorizontalMotionTracker.HorizontalMotion motion,
        final VanillaStepSimulator.StepResult step
    ) {
        if (confirmedSuppression || !step.stepable()) {
            return false;
        }

        return alignedProgress(
            motion,
            step.resolvedX(),
            step.resolvedZ()
        );
    }

    boolean canArmProjectedStep(
        final ClientHorizontalMotionTracker.HorizontalMotion motion,
        final ProjectedStepSimulator.StepResult step
    ) {
        return this.assessProjectedStep(motion, step).accepted();
    }

    ProjectedStepAssessment assessProjectedStep(
        final ClientHorizontalMotionTracker.HorizontalMotion motion,
        final ProjectedStepSimulator.StepResult step
    ) {
        if (!step.stepable()) {
            return new ProjectedStepAssessment(
                ProjectedStepStatus.NOT_STEP,
                Double.NaN
            );
        }
        if (!hasValidLandingGeometry(step)) {
            return new ProjectedStepAssessment(
                ProjectedStepStatus.INVALID_LANDING_GEOMETRY,
                Double.NaN
            );
        }

        final double reachableDistance = motion.speed()
            * PROJECTED_REACH_MULTIPLIER + PROJECTED_REACH_PADDING;
        if (step.distance() > MAX_PROJECTED_RELEASE_DISTANCE
            || step.distance() > reachableDistance) {
            return new ProjectedStepAssessment(
                ProjectedStepStatus.TARGET_BEYOND_REACHABLE_DISTANCE,
                reachableDistance
            );
        }
        if (!alignedProgress(motion, step.offsetX(), step.offsetZ())) {
            return new ProjectedStepAssessment(
                ProjectedStepStatus.INSUFFICIENT_MOTION,
                reachableDistance
            );
        }
        return new ProjectedStepAssessment(
            ProjectedStepStatus.ACCEPTED,
            reachableDistance
        );
    }

    boolean canArmStableProjectedStep(
        final boolean confirmedSuppression,
        final int consecutiveObservations,
        final ProjectedStepSimulator.StepResult step
    ) {
        return confirmedSuppression
            && consecutiveObservations
                >= StableStepCandidateTracker.REQUIRED_CONSECUTIVE_OBSERVATIONS
            && isPlausibleProjectedStep(step);
    }

    private static boolean isPlausibleProjectedStep(
        final ProjectedStepSimulator.StepResult step
    ) {
        return step.stepable()
            && hasValidLandingGeometry(step)
            && step.distance() <= MAX_PROJECTED_RELEASE_DISTANCE;
    }

    private static boolean hasValidLandingGeometry(
        final ProjectedStepSimulator.StepResult step
    ) {
        return Double.isFinite(step.distance())
            && step.distance() > 0.0D
            && Double.isFinite(step.rise())
            && step.rise() > 0.0D
            && Double.isFinite(step.offsetX())
            && Double.isFinite(step.offsetZ());
    }

    private static boolean alignedProgress(
        final ClientHorizontalMotionTracker.HorizontalMotion motion,
        final double targetX,
        final double targetZ
    ) {
        final double motionDistance = motion.speed();
        final double targetDistance = Math.hypot(targetX, targetZ);
        if (!Double.isFinite(motionDistance)
            || !Double.isFinite(targetDistance)
            || motionDistance < MIN_HORIZONTAL_PROGRESS
            || targetDistance < MIN_TARGET_DISTANCE) {
            return false;
        }

        final double alignment = (motion.x() * targetX + motion.z() * targetZ)
            / (motionDistance * targetDistance);
        return Double.isFinite(alignment) && alignment >= MIN_ALIGNMENT_COSINE;
    }

    enum ProjectedStepStatus {
        ACCEPTED,
        NOT_STEP,
        INSUFFICIENT_MOTION,
        TARGET_BEYOND_REACHABLE_DISTANCE,
        INVALID_LANDING_GEOMETRY
    }

    record ProjectedStepAssessment(
        ProjectedStepStatus status,
        double reachableDistance
    ) {
        boolean accepted() {
            return this.status == ProjectedStepStatus.ACCEPTED;
        }
    }
}

package dev.waterloggedjumpfix;

/** Rejects step predictions that are not backed by current horizontal progress. */
final class StepCandidateGate {
    private static final double MIN_HORIZONTAL_PROGRESS = 0.02D;
    private static final double MIN_TARGET_DISTANCE = 1.0E-5D;
    private static final double MIN_ALIGNMENT_COSINE = 0.5D;
    private static final double PROJECTED_REACH_MULTIPLIER = 1.25D;
    private static final double PROJECTED_REACH_PADDING = 0.025D;
    private static final double MAX_PROJECTED_RELEASE_DISTANCE = 0.2D;

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
        if (!step.stepable()
            || !Double.isFinite(step.distance())
            || step.distance() <= 0.0D
            || step.distance() > MAX_PROJECTED_RELEASE_DISTANCE) {
            return false;
        }

        final double reachableDistance = motion.speed()
            * PROJECTED_REACH_MULTIPLIER + PROJECTED_REACH_PADDING;
        return step.distance() <= reachableDistance
            && alignedProgress(motion, step.offsetX(), step.offsetZ());
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
}

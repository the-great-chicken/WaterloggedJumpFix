package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepCandidateGateTest {
    private final StepCandidateGate gate = new StepCandidateGate();

    @Test
    void trustsARealVanillaStepWithObservedProgress() {
        final var motion = new ClientHorizontalMotionTracker.HorizontalMotion(
            -0.23873D,
            0.16825D
        );
        final var step = new VanillaStepSimulator.StepResult(
            true,
            -0.04794D,
            0.5D,
            0.20555D
        );

        assertTrue(this.gate.trustsVanillaStep(false, motion, step));
    }

    @Test
    void rejectsPostRollbackVanillaStepWithoutProgress() {
        final var step = new VanillaStepSimulator.StepResult(
            true,
            0.0D,
            0.5D,
            0.07239D
        );

        assertFalse(
            this.gate.trustsVanillaStep(
                true,
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                step
            )
        );
    }

    @Test
    void armsTheObservedWallToStepTransition() {
        final var motion = new ClientHorizontalMotionTracker.HorizontalMotion(
            0.0D,
            0.12441D
        );
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.01795D,
            0.5D,
            0.0D,
            0.01795D
        );

        assertTrue(this.gate.canArmProjectedStep(motion, step));
    }

    @Test
    void doesNotRearmFromAStationaryPrediction() {
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.018D,
            0.5D,
            0.0D,
            0.018D
        );

        assertFalse(
            this.gate.canArmProjectedStep(
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                step
            )
        );
        assertEquals(
            StepCandidateGate.ProjectedStepStatus.INSUFFICIENT_MOTION,
            this.gate.assessProjectedStep(
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                step
            ).status()
        );
    }

    @Test
    void armsAStablePredictionDuringConfirmedSuppression() {
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.018D,
            0.5D,
            0.0D,
            0.018D
        );

        assertTrue(
            this.gate.canArmStableProjectedStep(
                true,
                StableStepCandidateTracker.REQUIRED_CONSECUTIVE_OBSERVATIONS,
                step
            )
        );
    }

    @Test
    void stablePredictionStillRequiresConfirmedSuppression() {
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.018D,
            0.5D,
            0.0D,
            0.018D
        );

        assertFalse(
            this.gate.canArmStableProjectedStep(
                false,
                StableStepCandidateTracker.REQUIRED_CONSECUTIVE_OBSERVATIONS,
                step
            )
        );
    }

    @Test
    void stablePredictionRequiresTwoMatchingObservations() {
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.018D,
            0.5D,
            0.0D,
            0.018D
        );

        assertFalse(this.gate.canArmStableProjectedStep(true, 1, step));
    }

    @Test
    void rejectsAProjectedStepFartherThanCurrentMotionCanReach() {
        final var motion = new ClientHorizontalMotionTracker.HorizontalMotion(
            0.0D,
            0.03D
        );
        final var step = new ProjectedStepSimulator.StepResult(
            true,
            0.15D,
            0.5D,
            0.0D,
            0.15D
        );

        assertFalse(this.gate.canArmProjectedStep(motion, step));
        assertEquals(
            StepCandidateGate.ProjectedStepStatus
                .TARGET_BEYOND_REACHABLE_DISTANCE,
            this.gate.assessProjectedStep(motion, step).status()
        );
    }

    @Test
    void distinguishesNotStepFromInvalidLandingGeometry() {
        final var notStep = new ProjectedStepSimulator.StepResult(
            false,
            Double.NaN,
            Double.NaN,
            0.0D,
            0.0D
        );
        final var invalidStep = new ProjectedStepSimulator.StepResult(
            true,
            0.02D,
            Double.NaN,
            0.0D,
            0.02D
        );

        assertEquals(
            StepCandidateGate.ProjectedStepStatus.NOT_STEP,
            this.gate.assessProjectedStep(
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                notStep
            ).status()
        );
        assertEquals(
            StepCandidateGate.ProjectedStepStatus.INVALID_LANDING_GEOMETRY,
            this.gate.assessProjectedStep(
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                invalidStep
            ).status()
        );
    }

    @Test
    void limitsProjectedSimulationToDistancesTheGateCanAccept() {
        assertEquals(0.1D, this.gate.projectedProbeDistance(0.1D));
        assertEquals(0.2D, this.gate.projectedProbeDistance(0.6D));
        assertEquals(0.0D, this.gate.projectedProbeDistance(Double.NaN));
        assertEquals(0.0D, this.gate.projectedProbeDistance(-0.1D));
    }
}

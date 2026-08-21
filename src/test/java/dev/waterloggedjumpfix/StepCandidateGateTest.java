package dev.waterloggedjumpfix;

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
            -0.20749D,
            0.20555D,
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
            -0.06899D,
            0.07239D,
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
    }
}

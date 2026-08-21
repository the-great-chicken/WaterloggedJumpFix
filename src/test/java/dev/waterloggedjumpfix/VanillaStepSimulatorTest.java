package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VanillaStepSimulatorTest {
    @Test
    void resolvesPaperCollisionInternalsAtStartup() {
        assertDoesNotThrow(VanillaStepSimulator::new);
    }

    @Test
    void acceptsResolvedHorizontalMovementWithAnUpwardStep() {
        assertTrue(
            VanillaStepSimulator.fromResolvedMovement(
                0.08D,
                0.5D,
                0.08D
            ).stepable()
        );
    }

    @Test
    void rejectsFlatOrFullyBlockedResolution() {
        assertFalse(
            VanillaStepSimulator.fromResolvedMovement(
                0.1D,
                0.0D,
                0.0D
            ).stepable()
        );
        assertFalse(
            VanillaStepSimulator.fromResolvedMovement(
                0.0D,
                0.5D,
                0.0D
            ).stepable()
        );
    }
}

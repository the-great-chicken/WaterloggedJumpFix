package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaterMovementDampingTest {
    private static final double TOLERANCE = 1.0E-12D;

    @Test
    void usesVanillaWalkingAndSprintingDamping() {
        assertEquals(
            0.8D,
            WaterMovementDamping.calculate(false, true, 0.0D, false),
            TOLERANCE
        );
        assertEquals(
            0.9D,
            WaterMovementDamping.calculate(true, true, 0.0D, false),
            TOLERANCE
        );
    }

    @Test
    void efficiencyInterpolatesTowardEfficientTarget() {
        assertEquals(
            0.546000063419342D,
            WaterMovementDamping.calculate(false, true, 1.0D, false),
            TOLERANCE
        );
        assertEquals(
            0.673000031709671D,
            WaterMovementDamping.calculate(false, false, 1.0D, false),
            TOLERANCE
        );
    }

    @Test
    void dolphinsGraceOverridesOtherDamping() {
        assertEquals(
            0.9599999785423279D,
            WaterMovementDamping.calculate(false, true, 1.0D, true),
            TOLERANCE
        );
    }
}

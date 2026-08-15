package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegitimateStepDetectorTest {
    @Test
    void acceptsHorizontalRiseWithinEffectiveStepHeight() {
        assertTrue(
            LegitimateStepDetector.hasPlausibleStepDisplacement(0.5D, 0.6D, 0.04D)
        );
        assertTrue(
            LegitimateStepDetector.hasPlausibleStepDisplacement(
                0.600005D,
                0.6D,
                0.04D
            )
        );
    }

    @Test
    void rejectsRiseAboveEffectiveStepHeight() {
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(0.61D, 0.6D, 0.04D)
        );
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(0.5D, 0.4D, 0.04D)
        );
    }

    @Test
    void rejectsVerticalOrInvalidDisplacement() {
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(0.5D, 0.6D, 0.0D)
        );
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(0.0D, 0.6D, 0.04D)
        );
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(
                Double.NaN,
                0.6D,
                0.04D
            )
        );
        assertFalse(
            LegitimateStepDetector.hasPlausibleStepDisplacement(
                0.5D,
                Double.POSITIVE_INFINITY,
                0.04D
            )
        );
    }
}

package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShallowWaterContactDetectorTest {
    @Test
    void acceptsBodyContactBelowTheEyes() {
        assertTrue(ShallowWaterContactDetector.isShallowWater(true, false));
    }

    @Test
    void rejectsDryOrFullySubmergedPlayers() {
        assertFalse(ShallowWaterContactDetector.isShallowWater(false, false));
        assertFalse(ShallowWaterContactDetector.isShallowWater(false, true));
        assertFalse(ShallowWaterContactDetector.isShallowWater(true, true));
    }
}

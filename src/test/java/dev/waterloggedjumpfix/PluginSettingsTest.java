package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void zeroDisablesPrediction() {
        assertEquals(0.0D, PluginSettings.normalizePredictionDistance(0.0D));
    }

    @Test
    void validDistanceIsPreserved() {
        assertEquals(0.02D, PluginSettings.normalizePredictionDistance(0.02D));
        assertEquals(1.0D, PluginSettings.normalizePredictionDistance(1.0D));
    }

    @Test
    void invalidDistanceFallsBackToZero() {
        assertEquals(0.0D, PluginSettings.normalizePredictionDistance(-0.01D));
        assertEquals(0.0D, PluginSettings.normalizePredictionDistance(1.01D));
        assertEquals(0.0D, PluginSettings.normalizePredictionDistance(Double.NaN));
        assertEquals(
            0.0D,
            PluginSettings.normalizePredictionDistance(Double.POSITIVE_INFINITY)
        );
    }
}

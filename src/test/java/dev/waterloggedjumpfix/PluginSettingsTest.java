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
        assertEquals(2.0D, PluginSettings.normalizePredictionDistance(2.0D));
    }

    @Test
    void invalidPredictionDistanceUsesItsDefault() {
        assertEquals(
            PluginSettings.DEFAULT_PREDICTION_DISTANCE,
            PluginSettings.normalizePredictionDistance(-0.01D)
        );
        assertEquals(
            PluginSettings.DEFAULT_PREDICTION_DISTANCE,
            PluginSettings.normalizePredictionDistance(2.01D)
        );
        assertEquals(
            PluginSettings.DEFAULT_PREDICTION_DISTANCE,
            PluginSettings.normalizePredictionDistance(Double.NaN)
        );
    }

    @Test
    void maximumDistanceMustBePositiveAndBounded() {
        assertEquals(0.6D, PluginSettings.normalizeMaximumDistance(0.6D));
        assertEquals(
            PluginSettings.DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE,
            PluginSettings.normalizeMaximumDistance(0.0D)
        );
        assertEquals(
            PluginSettings.DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE,
            PluginSettings.normalizeMaximumDistance(Double.POSITIVE_INFINITY)
        );
    }
}

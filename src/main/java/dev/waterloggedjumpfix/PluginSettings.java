package dev.waterloggedjumpfix;

import org.bukkit.plugin.java.JavaPlugin;

/** Validated plugin configuration. */
record PluginSettings(
    double predictionDistance,
    double timeLookaheadMaxDistance
) {
    static final double DEFAULT_PREDICTION_DISTANCE = 0.1D;
    static final double DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE = 0.6D;
    static final double MAX_CONFIGURED_DISTANCE = 2.0D;

    static PluginSettings load(final JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        final double configuredPredictionDistance = plugin.getConfig().getDouble(
            "prediction-distance",
            DEFAULT_PREDICTION_DISTANCE
        );
        final double predictionDistance = normalizePredictionDistance(
            configuredPredictionDistance
        );
        if (Double.compare(
            configuredPredictionDistance,
            predictionDistance
        ) != 0) {
            plugin.getLogger().warning(
                "prediction-distance must be a finite number between 0.0 and "
                    + MAX_CONFIGURED_DISTANCE + "; using "
                    + DEFAULT_PREDICTION_DISTANCE + "."
            );
        }

        final double configuredMaximumDistance = plugin.getConfig().getDouble(
            "time-lookahead-max-distance",
            DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE
        );
        double maximumDistance = normalizeMaximumDistance(
            configuredMaximumDistance
        );
        if (Double.compare(configuredMaximumDistance, maximumDistance) != 0) {
            plugin.getLogger().warning(
                "time-lookahead-max-distance must be a finite number greater "
                    + "than 0.0 and at most " + MAX_CONFIGURED_DISTANCE
                    + "; using " + DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE + "."
            );
        }
        if (maximumDistance < predictionDistance) {
            plugin.getLogger().warning(
                "time-lookahead-max-distance cannot be smaller than "
                    + "prediction-distance; using prediction-distance."
            );
            maximumDistance = predictionDistance;
        }

        return new PluginSettings(predictionDistance, maximumDistance);
    }

    static double normalizePredictionDistance(final double configured) {
        if (!Double.isFinite(configured)
            || configured < 0.0D
            || configured > MAX_CONFIGURED_DISTANCE) {
            return DEFAULT_PREDICTION_DISTANCE;
        }
        return configured;
    }

    static double normalizeMaximumDistance(final double configured) {
        if (!Double.isFinite(configured)
            || configured <= 0.0D
            || configured > MAX_CONFIGURED_DISTANCE) {
            return DEFAULT_TIME_LOOKAHEAD_MAX_DISTANCE;
        }
        return configured;
    }
}

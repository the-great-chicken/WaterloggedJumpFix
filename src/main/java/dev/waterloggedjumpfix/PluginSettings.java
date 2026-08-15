package dev.waterloggedjumpfix;

import org.bukkit.plugin.java.JavaPlugin;

/** Validated plugin configuration. */
record PluginSettings(double predictionDistance) {
    static final double DEFAULT_PREDICTION_DISTANCE = 0.0D;
    static final double MAX_PREDICTION_DISTANCE = 1.0D;

    static PluginSettings load(final JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        final double configured = plugin.getConfig().getDouble(
            "prediction-distance",
            DEFAULT_PREDICTION_DISTANCE
        );
        final double predictionDistance = normalizePredictionDistance(configured);
        if (Double.compare(configured, predictionDistance) != 0) {
            plugin.getLogger().warning(
                "prediction-distance must be a finite number between 0.0 and "
                    + MAX_PREDICTION_DISTANCE + "; using 0.0."
            );
        }
        return new PluginSettings(predictionDistance);
    }

    static double normalizePredictionDistance(final double configured) {
        if (!Double.isFinite(configured)
            || configured < 0.0D
            || configured > MAX_PREDICTION_DISTANCE) {
            return DEFAULT_PREDICTION_DISTANCE;
        }
        return configured;
    }
}

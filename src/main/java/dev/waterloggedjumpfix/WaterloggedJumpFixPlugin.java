package dev.waterloggedjumpfix;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper workaround for MC-8959, including its waterlogged slab/stair
 * reproduction, MC-174654.
 */
public final class WaterloggedJumpFixPlugin extends JavaPlugin {
    private final RecentPlayerActivity recentActivity = new RecentPlayerActivity();
    private final ConfirmedSuppression confirmedSuppression = new ConfirmedSuppression();
    private final ClientHorizontalMotionTracker motionTracker =
        new ClientHorizontalMotionTracker();

    @Override
    public void onEnable() {
        final PluginSettings settings = PluginSettings.load(this);
        this.getServer().getPluginManager().registerEvents(
            new WaterloggedJumpListener(
                this.recentActivity,
                this.confirmedSuppression,
                this.motionTracker,
                new ShallowWaterContactDetector(),
                new HorizontalCollisionProbe(),
                new LegitimateStepDetector(),
                new WaterMovementDamping(),
                new ClientMotionSuppressor(),
                settings
            ),
            this
        );

        this.getLogger().info(
            "MC-8959 workaround enabled globally (prediction-distance: "
                + settings.predictionDistance() + ")."
        );
    }

    @Override
    public void onDisable() {
        this.recentActivity.clear();
        this.confirmedSuppression.clear();
        this.motionTracker.clear();
    }
}

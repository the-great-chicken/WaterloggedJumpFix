package dev.waterloggedjumpfix;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper workaround for MC-8959, including its waterlogged slab/stair
 * reproduction, MC-174654.
 */
public final class WaterloggedJumpFixPlugin extends JavaPlugin {
    private final RecentPlayerActivity recentActivity = new RecentPlayerActivity();
    private final ConfirmedSuppression confirmedSuppression = new ConfirmedSuppression();
    private final RecentWallContactTracker recentWallContact =
        new RecentWallContactTracker();
    private final ClientHorizontalMotionTracker motionTracker =
        new ClientHorizontalMotionTracker();

    @Override
    public void onEnable() {
        final PluginSettings settings = PluginSettings.load(this);
        this.getServer().getPluginManager().registerEvents(
            new WaterloggedJumpListener(
                this.recentActivity,
                this.confirmedSuppression,
                this.recentWallContact,
                this.motionTracker,
                new ShallowWaterContactDetector(),
                new HorizontalCollisionProbe(),
                new LegitimateStepDetector(),
                new CollisionLookahead(),
                new WaterMovementDamping(),
                new ClientMotionSuppressor(),
                settings
            ),
            this
        );

        this.getLogger().info(
            "MC-8959 workaround enabled globally (latency-aware lookahead: "
                + settings.predictionDistance() + " to "
                + settings.timeLookaheadMaxDistance() + " blocks)."
        );
    }

    @Override
    public void onDisable() {
        this.recentActivity.clear();
        this.confirmedSuppression.clear();
        this.recentWallContact.clear();
        this.motionTracker.clear();
    }
}

package dev.waterloggedjumpfix;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper workaround for MC-8959 and its waterlogged slab/stair reproduction,
 * MC-174654.
 */
public final class WaterloggedJumpFixPlugin extends JavaPlugin {
    private final RecentPlayerActivity recentActivity = new RecentPlayerActivity();

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(
            new WaterloggedJumpListener(
                this.recentActivity,
                new WaterloggedContactDetector(),
                new HorizontalCollisionProbe()
            ),
            this
        );

        this.getLogger().info(
            "MC-8959 waterlogged slab/stair workaround enabled globally."
        );
    }

    @Override
    public void onDisable() {
        this.recentActivity.clear();
    }
}

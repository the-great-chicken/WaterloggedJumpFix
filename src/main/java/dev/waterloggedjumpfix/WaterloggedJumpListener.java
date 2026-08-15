package dev.waterloggedjumpfix;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;

/** Applies the targeted cancellation and maintains its safety exemptions. */
final class WaterloggedJumpListener implements Listener {
    private final RecentPlayerActivity recentActivity;
    private final WaterloggedContactDetector contactDetector;
    private final HorizontalCollisionProbe collisionProbe;

    WaterloggedJumpListener(
        final RecentPlayerActivity recentActivity,
        final WaterloggedContactDetector contactDetector,
        final HorizontalCollisionProbe collisionProbe
    ) {
        this.recentActivity = recentActivity;
        this.contactDetector = contactDetector;
        this.collisionProbe = collisionProbe;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(final PlayerInputEvent event) {
        if (event.getInput().isJump()) {
            this.recentActivity.recordJumpInput(
                event.getPlayer().getUniqueId(),
                Bukkit.getCurrentTick()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(final EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player player) {
            this.recentActivity.recordExternalVelocity(
                player.getUniqueId(),
                Bukkit.getCurrentTick()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(final PlayerVelocityEvent event) {
        this.recentActivity.recordExternalVelocity(
            event.getPlayer().getUniqueId(),
            Bukkit.getCurrentTick()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJump(final PlayerJumpEvent event) {
        final Player player = event.getPlayer();
        if (!isEligibleMovementState(player)) {
            return;
        }

        final int currentTick = Bukkit.getCurrentTick();
        final Input input = player.getCurrentInput();
        if (input.isJump()
            || this.recentActivity.hasRecentJumpInput(player.getUniqueId(), currentTick)
            || this.recentActivity.hasRecentExternalVelocity(player.getUniqueId(), currentTick)) {
            return;
        }

        final Location origin = event.getFrom();
        if (!this.contactDetector.isTouchingTarget(player, origin)
            || !this.collisionProbe.isMovingIntoCollision(
                player,
                origin,
                event.getTo().getYaw(),
                input
            )) {
            return;
        }

        // Retain a simultaneous camera turn while Paper returns the player to
        // the safe pre-hop position.
        final Location rollbackLocation = origin.clone();
        rollbackLocation.setYaw(event.getTo().getYaw());
        rollbackLocation.setPitch(event.getTo().getPitch());
        event.setFrom(rollbackLocation);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        this.recentActivity.forget(event.getPlayer().getUniqueId());
    }

    private static boolean isEligibleMovementState(final Player player) {
        final GameMode gameMode = player.getGameMode();
        if (gameMode != GameMode.SURVIVAL && gameMode != GameMode.ADVENTURE) {
            return false;
        }

        return !player.isFlying()
            && !player.isGliding()
            && !player.isRiptiding()
            && !player.isSwimming()
            && !player.isInsideVehicle()
            && !player.hasPotionEffect(PotionEffectType.LEVITATION);
    }
}

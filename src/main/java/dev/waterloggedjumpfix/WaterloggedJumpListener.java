package dev.waterloggedjumpfix;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.packet.ClientTickEndEvent;
import java.util.UUID;
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
    private static final double CONFIRMED_CONTACT_PROBE_DISTANCE = 0.08D;
    private static final double MAX_SAFE_ROLLBACK_HORIZONTAL_DISTANCE_SQUARED = 0.25D;

    private final RecentPlayerActivity recentActivity;
    private final ConfirmedSuppression confirmedSuppression;
    private final ClientHorizontalMotionTracker motionTracker;
    private final WaterloggedContactDetector contactDetector;
    private final HorizontalCollisionProbe collisionProbe;
    private final WaterMovementDamping movementDamping;
    private final ClientMotionSuppressor motionSuppressor;
    private final PluginSettings settings;

    WaterloggedJumpListener(
        final RecentPlayerActivity recentActivity,
        final ConfirmedSuppression confirmedSuppression,
        final ClientHorizontalMotionTracker motionTracker,
        final WaterloggedContactDetector contactDetector,
        final HorizontalCollisionProbe collisionProbe,
        final WaterMovementDamping movementDamping,
        final ClientMotionSuppressor motionSuppressor,
        final PluginSettings settings
    ) {
        this.recentActivity = recentActivity;
        this.confirmedSuppression = confirmedSuppression;
        this.motionTracker = motionTracker;
        this.contactDetector = contactDetector;
        this.collisionProbe = collisionProbe;
        this.movementDamping = movementDamping;
        this.motionSuppressor = motionSuppressor;
        this.settings = settings;
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

        final Location origin = player.getLocation();
        if (!this.contactDetector.isTouchingTarget(player, origin)
            || !this.collisionProbe.isMovingIntoCollision(
                player,
                origin,
                event.getTo().getYaw(),
                input,
                CONFIRMED_CONTACT_PROBE_DISTANCE
            )) {
            return;
        }

        this.confirmedSuppression.confirm(player.getUniqueId());
        event.setFrom(this.selectRollbackLocation(player, origin, event.getTo()));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientTickEnd(final ClientTickEndEvent event) {
        final Player player = event.getPlayer();
        final ClientHorizontalMotionTracker.HorizontalMotion horizontalMotion =
            this.motionTracker.observe(player);
        final UUID playerId = player.getUniqueId();

        if (!isEligibleMovementState(player)) {
            this.confirmedSuppression.forget(playerId);
            return;
        }

        final int currentTick = Bukkit.getCurrentTick();
        final Input input = player.getCurrentInput();
        if (input.isJump()
            || this.recentActivity.hasRecentJumpInput(playerId, currentTick)
            || this.recentActivity.hasRecentExternalVelocity(playerId, currentTick)) {
            this.confirmedSuppression.forget(playerId);
            return;
        }

        final Location origin = player.getLocation();
        if (!this.contactDetector.isTouchingTarget(player, origin)) {
            this.confirmedSuppression.forget(playerId);
            return;
        }

        final boolean confirmed = this.confirmedSuppression.isConfirmed(playerId);
        final double probeDistance = confirmed
            ? CONFIRMED_CONTACT_PROBE_DISTANCE
            : this.settings.predictionDistance();
        if (!this.collisionProbe.isMovingIntoCollision(
            player,
            origin,
            origin.getYaw(),
            input,
            probeDistance
        )) {
            if (confirmed) {
                this.confirmedSuppression.forget(playerId);
            }
            return;
        }

        final double damping = this.movementDamping.forPlayer(player);
        this.motionSuppressor.clearUpwardMotion(
            player,
            horizontalMotion.damped(damping)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        this.recentActivity.forget(playerId);
        this.confirmedSuppression.forget(playerId);
        this.motionTracker.forget(playerId);
    }

    private Location selectRollbackLocation(
        final Player player,
        final Location current,
        final Location requested
    ) {
        final Location rollback = current.clone();
        final double deltaX = requested.getX() - current.getX();
        final double deltaZ = requested.getZ() - current.getZ();
        final double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (Double.isFinite(distanceSquared)
            && distanceSquared <= MAX_SAFE_ROLLBACK_HORIZONTAL_DISTANCE_SQUARED) {
            final Location horizontalAdvance = rollback.clone();
            horizontalAdvance.setX(requested.getX());
            horizontalAdvance.setZ(requested.getZ());
            if (!player.collidesAt(horizontalAdvance)) {
                rollback.setX(requested.getX());
                rollback.setZ(requested.getZ());
            }
        }

        rollback.setYaw(requested.getYaw());
        rollback.setPitch(requested.getPitch());
        return rollback;
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

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

/** Applies predictive motion suppression and maintains its safety exemptions. */
final class WaterloggedJumpListener implements Listener {
    private static final double CONFIRMED_CONTACT_PROBE_DISTANCE = 0.08D;
    private static final double MAX_SAFE_ROLLBACK_HORIZONTAL_DISTANCE_SQUARED =
        0.25D;

    private final RecentPlayerActivity recentActivity;
    private final ConfirmedSuppression confirmedSuppression;
    private final RecentWallContactTracker recentWallContact;
    private final ClientHorizontalMotionTracker motionTracker;
    private final ShallowWaterContactDetector contactDetector;
    private final HorizontalCollisionProbe collisionProbe;
    private final LegitimateStepDetector stepDetector;
    private final VanillaStepSimulator vanillaStepSimulator;
    private final ProjectedStepSimulator projectedStepSimulator;
    private final StepCandidateGate stepCandidateGate;
    private final StepTransitionPermitTracker stepPermits;
    private final CollisionLookahead collisionLookahead;
    private final WaterMovementDamping movementDamping;
    private final ClientMotionSuppressor motionSuppressor;
    private final PluginSettings settings;

    WaterloggedJumpListener(
        final RecentPlayerActivity recentActivity,
        final ConfirmedSuppression confirmedSuppression,
        final RecentWallContactTracker recentWallContact,
        final ClientHorizontalMotionTracker motionTracker,
        final ShallowWaterContactDetector contactDetector,
        final HorizontalCollisionProbe collisionProbe,
        final LegitimateStepDetector stepDetector,
        final VanillaStepSimulator vanillaStepSimulator,
        final ProjectedStepSimulator projectedStepSimulator,
        final StepCandidateGate stepCandidateGate,
        final StepTransitionPermitTracker stepPermits,
        final CollisionLookahead collisionLookahead,
        final WaterMovementDamping movementDamping,
        final ClientMotionSuppressor motionSuppressor,
        final PluginSettings settings
    ) {
        this.recentActivity = recentActivity;
        this.confirmedSuppression = confirmedSuppression;
        this.recentWallContact = recentWallContact;
        this.motionTracker = motionTracker;
        this.contactDetector = contactDetector;
        this.collisionProbe = collisionProbe;
        this.stepDetector = stepDetector;
        this.vanillaStepSimulator = vanillaStepSimulator;
        this.projectedStepSimulator = projectedStepSimulator;
        this.stepCandidateGate = stepCandidateGate;
        this.stepPermits = stepPermits;
        this.collisionLookahead = collisionLookahead;
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
        final UUID playerId = player.getUniqueId();
        if (!isEligibleMovementState(player)) {
            return;
        }

        final int currentTick = Bukkit.getCurrentTick();
        final Input input = player.getCurrentInput();
        if (input.isJump()
            || this.recentActivity.hasRecentJumpInput(playerId, currentTick)
            || this.recentActivity.hasRecentExternalVelocity(playerId, currentTick)) {
            return;
        }

        final Location origin = player.getLocation();
        final Location requested = event.getTo();
        if (this.stepDetector.isLegitimateStep(player, origin, requested)) {
            this.stepPermits.complete(playerId);
            this.forgetSuppressionState(playerId);
            return;
        }

        if (!this.contactDetector.isInShallowWater(player)) {
            return;
        }

        final UUID worldId = player.getWorld().getUID();
        final HorizontalCollisionProbe.MovementDirection currentDirection =
            HorizontalCollisionProbe.movementDirection(requested.getYaw(), input);
        final RecentWallContactTracker.DirectionResolution effectiveDirection =
            this.recentWallContact.resolve(
                playerId,
                worldId,
                currentDirection
            );
        if (!effectiveDirection.isAvailable()) {
            return;
        }

        if (this.stepPermits.activate(
            playerId,
            worldId,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            effectiveDirection.direction()
        )) {
            this.forgetSuppressionState(playerId);
            return;
        }

        if (!this.collisionProbe.isMovingIntoCollision(
            player,
            origin,
            effectiveDirection.direction(),
            CONFIRMED_CONTACT_PROBE_DISTANCE
        )) {
            return;
        }

        this.confirmedSuppression.confirm(
            playerId,
            worldId,
            effectiveDirection.direction(),
            origin.getY()
        );
        this.recentWallContact.recordContact(
            playerId,
            worldId,
            effectiveDirection.direction()
        );
        event.setFrom(this.selectRollbackLocation(player, origin, requested));
        event.setCancelled(true);
        this.sendMotionReset(player, effectiveDirection.direction());
        this.stepPermits.block(
            playerId,
            worldId,
            origin.getX(),
            origin.getZ(),
            effectiveDirection.direction()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientTickEnd(final ClientTickEndEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        this.recentWallContact.advanceClientTick(playerId);
        this.stepPermits.advanceClientTick(playerId);
        final ClientHorizontalMotionTracker.HorizontalMotion horizontalMotion =
            this.motionTracker.observe(player);
        final int currentTick = Bukkit.getCurrentTick();
        final Input input = player.getCurrentInput();
        final Location origin = player.getLocation();
        final UUID worldId = player.getWorld().getUID();
        final HorizontalCollisionProbe.MovementDirection currentDirection =
            HorizontalCollisionProbe.movementDirection(origin.getYaw(), input);

        if (!isEligibleMovementState(player)
            || input.isJump()
            || this.recentActivity.hasRecentJumpInput(playerId, currentTick)
            || this.recentActivity.hasRecentExternalVelocity(playerId, currentTick)) {
            this.forgetMovementState(playerId);
            return;
        }

        if (this.handlePendingStepPermit(player, origin, worldId)) {
            return;
        }

        if (!this.contactDetector.isInShallowWater(player)) {
            this.handleOutsideShallowWater(
                player,
                playerId,
                worldId,
                origin,
                currentDirection
            );
            return;
        }

        final double lookaheadDistance = this.collisionLookahead.distance(
            this.settings.predictionDistance(),
            this.settings.timeLookaheadMaxDistance(),
            player.getPing(),
            horizontalMotion
        );
        final HorizontalCollisionProbe.CollisionResult predictedCollision =
            this.collisionProbe.firstCollision(
                player,
                origin,
                currentDirection,
                lookaheadDistance
            );
        final double stepProbeDistance = Math.min(
            this.settings.timeLookaheadMaxDistance(),
            Math.max(lookaheadDistance, horizontalMotion.speed())
        );
        final VanillaStepSimulator.StepResult proactiveStep =
            this.vanillaStepSimulator.assess(
                player,
                currentDirection,
                stepProbeDistance
            );
        final ProjectedStepSimulator.StepResult projectedStep =
            this.projectedStepSimulator.assess(
                player,
                origin,
                currentDirection,
                lookaheadDistance
            );

        if (this.stepCandidateGate.trustsVanillaStep(
            this.confirmedSuppression.isConfirmed(playerId),
            horizontalMotion,
            proactiveStep
        )) {
            this.stepPermits.complete(playerId);
            this.forgetSuppressionState(playerId);
            return;
        }

        if (projectedStep.stepable()
            && !this.stepPermits.isRearmBlocked(
                playerId,
                worldId,
                origin.getX(),
                origin.getZ(),
                currentDirection
            )
            && this.stepCandidateGate.canArmProjectedStep(
                horizontalMotion,
                projectedStep
            )) {
            this.stepPermits.arm(
                playerId,
                worldId,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                currentDirection,
                projectedStep
            );
            return;
        }

        final boolean currentCollision = currentDirection.isMoving()
            && this.collisionProbe.isMovingIntoCollision(
                player,
                origin,
                currentDirection,
                CONFIRMED_CONTACT_PROBE_DISTANCE
            );

        if (!this.confirmedSuppression.isConfirmed(playerId)) {
            this.handleUnconfirmedContact(
                player,
                origin,
                currentDirection,
                worldId,
                currentCollision,
                predictedCollision
            );
            return;
        }

        if (!this.confirmedSuppression.matchesWorld(playerId, worldId)) {
            this.forgetMovementState(playerId);
            return;
        }

        HorizontalCollisionProbe.MovementDirection effectiveDirection =
            currentDirection;
        final boolean contactCollision;
        if (currentDirection.isMoving()) {
            contactCollision = currentCollision;
            if (!currentCollision && !predictedCollision.collision()) {
                this.forgetMovementState(playerId);
                return;
            }
        } else {
            final RecentWallContactTracker.DirectionResolution recentDirection =
                this.recentWallContact.resolve(
                    playerId,
                    worldId,
                    currentDirection
                );
            if (!recentDirection.isAvailable()) {
                this.forgetMovementState(playerId);
                return;
            }
            effectiveDirection = recentDirection.direction();
            contactCollision = this.collisionProbe.isMovingIntoCollision(
                player,
                origin,
                effectiveDirection,
                CONFIRMED_CONTACT_PROBE_DISTANCE
            );
        }

        if (contactCollision || currentDirection.isMoving()) {
            this.confirmedSuppression.confirm(
                playerId,
                worldId,
                effectiveDirection,
                origin.getY()
            );
            if (contactCollision && currentDirection.isMoving()) {
                this.recentWallContact.recordContact(
                    playerId,
                    worldId,
                    effectiveDirection
                );
            }
        } else if (!this.confirmedSuppression.recordProbeMiss(playerId)) {
            this.forgetMovementState(playerId);
            return;
        }

        this.sendMotionReset(player, effectiveDirection);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        this.recentActivity.forget(playerId);
        this.forgetMovementState(playerId);
        this.motionTracker.forget(playerId);
    }

    private boolean handlePendingStepPermit(
        final Player player,
        final Location origin,
        final UUID worldId
    ) {
        final UUID playerId = player.getUniqueId();
        if (!this.stepPermits.isArmed(playerId)
            && !this.stepPermits.isActive(playerId)) {
            return false;
        }

        if (!this.stepPermits.matchesWorld(playerId, worldId)) {
            this.forgetMovementState(playerId);
            return true;
        }

        final Location supportProbe = origin.clone().subtract(0.0D, 0.05D, 0.0D);
        final boolean supported = player.isOnGround()
            || player.collidesAt(supportProbe);
        if (this.stepPermits.hasReachedTarget(
            playerId,
            origin.getY(),
            supported
        )) {
            this.stepPermits.complete(playerId);
            this.forgetSuppressionState(playerId);
            return true;
        }

        if (this.stepPermits.isArmedExpired(playerId)
            || this.stepPermits.isActiveExpired(playerId)) {
            final HorizontalCollisionProbe.MovementDirection direction =
                this.stepPermits.direction(playerId);
            this.sendMotionReset(player, direction);
            this.stepPermits.block(
                playerId,
                worldId,
                origin.getX(),
                origin.getZ(),
                direction
            );
        }
        return true;
    }

    private void handleUnconfirmedContact(
        final Player player,
        final Location origin,
        final HorizontalCollisionProbe.MovementDirection currentDirection,
        final UUID worldId,
        final boolean currentCollision,
        final HorizontalCollisionProbe.CollisionResult predictedCollision
    ) {
        final UUID playerId = player.getUniqueId();
        if (currentCollision) {
            this.recentWallContact.recordContact(
                playerId,
                worldId,
                currentDirection
            );
        }

        if (!predictedCollision.collision()) {
            if (currentDirection.isMoving() && !currentCollision) {
                this.recentWallContact.forget(playerId);
            }
            return;
        }

        this.sendMotionReset(player, currentDirection);
    }

    private void handleOutsideShallowWater(
        final Player player,
        final UUID playerId,
        final UUID worldId,
        final Location origin,
        final HorizontalCollisionProbe.MovementDirection currentDirection
    ) {
        if (!this.confirmedSuppression.isConfirmed(playerId)) {
            this.recentWallContact.forget(playerId);
            return;
        }
        if (!this.confirmedSuppression.matchesWorld(playerId, worldId)
            || !this.confirmedSuppression.recordAirborneRecoveryTick(
                playerId,
                worldId,
                origin.getY()
            )) {
            this.forgetMovementState(playerId);
            return;
        }

        final RecentWallContactTracker.DirectionResolution recentDirection =
            this.recentWallContact.resolve(
                playerId,
                worldId,
                currentDirection
            );
        final HorizontalCollisionProbe.MovementDirection effectiveDirection =
            recentDirection.isAvailable()
                ? recentDirection.direction()
                : this.confirmedSuppression.direction(playerId);
        this.sendMotionReset(player, effectiveDirection);
    }

    private void sendMotionReset(
        final Player player,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        final ClientHorizontalMotionTracker.HorizontalMotion sentMotion =
            this.motionTracker
                .latest(player.getUniqueId())
                .withoutOpposingComponents(direction)
                .damped(this.movementDamping.forPlayer(player));
        this.motionSuppressor.clearUpwardMotion(player, sentMotion);
    }

    private void forgetMovementState(final UUID playerId) {
        this.forgetSuppressionState(playerId);
        this.stepPermits.forget(playerId);
    }

    private void forgetSuppressionState(final UUID playerId) {
        this.confirmedSuppression.forget(playerId);
        this.recentWallContact.forget(playerId);
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

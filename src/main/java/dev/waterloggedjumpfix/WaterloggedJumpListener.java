package dev.waterloggedjumpfix;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.packet.ClientTickEndEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/** Applies predictive motion suppression and maintains its safety exemptions. */
final class WaterloggedJumpListener implements Listener {
    private static final double CONFIRMED_CONTACT_PROBE_DISTANCE = 0.08D;
    private static final double MAX_SAFE_ROLLBACK_HORIZONTAL_DISTANCE_SQUARED =
        0.25D;
    private static final double MOTION_RESTORE_EPSILON = 1.0E-5D;
    private static final double MAX_MOTION_RESTORE_HORIZONTAL_DISTANCE_SQUARED =
        0.5D * 0.5D;
    private static final double MAX_MOTION_RESTORE_VERTICAL_DISTANCE = 0.35D;
    private static final double MIN_MOTION_RESTORE_DIRECTION_DOT = 0.5D;

    private final JavaPlugin plugin;
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
    private final StableStepCandidateTracker stableStepCandidates;
    private final CollisionLookahead collisionLookahead;
    private final WaterMovementDamping movementDamping;
    private final ClientMotionSuppressor motionSuppressor;
    private final PluginSettings settings;
    private final Map<UUID, Integer> landingRestoreTokens = new HashMap<>();

    WaterloggedJumpListener(
        final JavaPlugin plugin,
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
        final StableStepCandidateTracker stableStepCandidates,
        final CollisionLookahead collisionLookahead,
        final WaterMovementDamping movementDamping,
        final ClientMotionSuppressor motionSuppressor,
        final PluginSettings settings
    ) {
        this.plugin = plugin;
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
        this.stableStepCandidates = stableStepCandidates;
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
        final Location originalEventFrom = event.getFrom().clone();
        final Location requested = event.getTo();
        final boolean legitimateRequestedStep =
            this.stepDetector.isLegitimateStep(player, origin, requested);

        if (!this.contactDetector.isInShallowWater(player)) {
            if (legitimateRequestedStep) {
                this.stepPermits.complete(playerId);
                this.forgetSuppressionState(playerId);
            }
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
        StepTransitionPermitTracker.BlockReason jumpBlockReason =
            StepTransitionPermitTracker.BlockReason.UNWANTED_JUMP;

        if (effectiveDirection.isAvailable()) {
            final StepTransitionPermitTracker.LandingAttempt landingAttempt =
                this.stepPermits.consumeLanding(
                    playerId,
                    worldId,
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    effectiveDirection.direction(),
                    stepHeight(player)
                );
            if (landingAttempt.accepted()) {
                final StepTransitionPermitTracker.Landing landing =
                    landingAttempt.landing();
                final Location landingLocation = new Location(
                    player.getWorld(),
                    landing.targetX(),
                    landing.targetY(),
                    landing.targetZ(),
                    requested.getYaw(),
                    requested.getPitch()
                );
                if (this.stepDetector.isLegitimateStep(
                    player,
                    origin,
                    landingLocation
                )) {
                    event.setFrom(landingLocation);
                    event.setCancelled(true);
                    this.stepPermits.complete(playerId);
                    this.forgetSuppressionState(playerId);
                    this.schedulePostLandingRestore(player, landing);
                    return;
                }
                jumpBlockReason =
                    StepTransitionPermitTracker.BlockReason.PERMIT_REJECTED;
            } else if (landingAttempt.hadPermit()) {
                jumpBlockReason = landingAttempt.status()
                        == StepTransitionPermitTracker.LandingStatus.EXPIRED
                    ? StepTransitionPermitTracker.BlockReason.PERMIT_EXPIRED
                    : StepTransitionPermitTracker.BlockReason.PERMIT_REJECTED;
            }
        }

        if (legitimateRequestedStep) {
            this.stepPermits.complete(playerId);
            this.forgetSuppressionState(playerId);
            return;
        }

        if (!effectiveDirection.isAvailable()) {
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
        final Location rollback = selectRollbackLocation(
            player,
            originalEventFrom,
            origin,
            requested
        );
        event.setFrom(rollback);
        event.setCancelled(true);
        this.sendMotionReset(player, effectiveDirection.direction());
        this.blockStepAttempt(
            playerId,
            worldId,
            origin,
            effectiveDirection.direction(),
            jumpBlockReason
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
        final int pingMillis = player.getPing();
        final Location origin = player.getLocation();
        final boolean shallowWater = this.contactDetector.isInShallowWater(player);
        final UUID worldId = player.getWorld().getUID();
        final HorizontalCollisionProbe.MovementDirection currentDirection =
            HorizontalCollisionProbe.movementDirection(origin.getYaw(), input);

        if (!isEligibleMovementState(player)
            || input.isJump()
            || this.recentActivity.hasRecentJumpInput(playerId, currentTick)
            || this.recentActivity.hasRecentExternalVelocity(
                playerId,
                currentTick
            )) {
            this.forgetMovementState(playerId);
            return;
        }

        if (this.handlePendingStepPermit(
            player,
            origin,
            worldId,
            currentDirection,
            shallowWater
        )) {
            return;
        }

        if (!shallowWater) {
            this.stableStepCandidates.forget(playerId);
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
            pingMillis,
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
                this.stepCandidateGate.projectedProbeDistance(lookaheadDistance)
            );
        final StepCandidateGate.ProjectedStepAssessment projectedAssessment =
            this.stepCandidateGate.assessProjectedStep(
                horizontalMotion,
                projectedStep
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

        final boolean suppressionConfirmed =
            this.confirmedSuppression.isConfirmed(playerId);
        final boolean suppressionWorldMatches =
            this.confirmedSuppression.matchesWorld(playerId, worldId);
        final boolean rearmBlocked = this.stepPermits.isRearmBlocked(
            playerId,
            worldId,
            origin.getX(),
            origin.getZ(),
            currentDirection
        );
        final boolean motionBackedStep = projectedStep.stepable()
            && !rearmBlocked
            && projectedAssessment.accepted();
        int stableObservations = 0;
        boolean stableGeometryReady = false;
        if (projectedStep.stepable()
            && !motionBackedStep
            && suppressionConfirmed
            && suppressionWorldMatches) {
            stableObservations = this.stableStepCandidates.observe(
                playerId,
                worldId,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                currentDirection,
                projectedStep
            );
            stableGeometryReady =
                this.stepCandidateGate.canArmStableProjectedStep(
                    true,
                    stableObservations,
                    projectedStep
                );
        } else {
            this.stableStepCandidates.forget(playerId);
        }
        final boolean stableGeometryStep = stableGeometryReady
            && !rearmBlocked;

        if (motionBackedStep || stableGeometryStep) {
            final StepTransitionPermitTracker.ArmSource armSource =
                motionBackedStep
                    ? StepTransitionPermitTracker.ArmSource.MOTION
                    : StepTransitionPermitTracker.ArmSource.STABLE_GEOMETRY;
            final double movementDamping = this.movementDamping.forPlayer(
                player
            );
            final ClientHorizontalMotionTracker.HorizontalMotion retainedMotion =
                this.motionTracker
                    .bestRecent(playerId, currentDirection)
                    .damped(movementDamping);
            this.stepPermits.arm(
                playerId,
                worldId,
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                currentDirection,
                projectedStep,
                retainedMotion,
                armSource
            );
            this.stableStepCandidates.forget(playerId);
            return;
        }

        final boolean currentCollision = currentDirection.isMoving()
            && this.collisionProbe.isMovingIntoCollision(
                player,
                origin,
                currentDirection,
                CONFIRMED_CONTACT_PROBE_DISTANCE
            );

        if (!suppressionConfirmed) {
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

        if (!suppressionWorldMatches) {
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
        this.forgetPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        this.forgetPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        this.forgetPlayer(event.getPlayer());
    }

    private void forgetPlayer(final Player player) {
        final UUID playerId = player.getUniqueId();
        this.landingRestoreTokens.remove(playerId);
        this.recentActivity.forget(playerId);
        this.forgetMovementState(playerId);
        this.motionTracker.forget(playerId);
    }

    private boolean handlePendingStepPermit(
        final Player player,
        final Location origin,
        final UUID worldId,
        final HorizontalCollisionProbe.MovementDirection currentDirection,
        final boolean shallowWater
    ) {
        final UUID playerId = player.getUniqueId();
        if (!this.stepPermits.isArmed(playerId)) {
            return false;
        }

        if (!this.stepPermits.matchesWorld(playerId, worldId)) {
            this.forgetMovementState(playerId);
            return true;
        }

        final Location supportProbe = origin.clone().subtract(0.0D, 0.05D, 0.0D);
        final boolean supported = ((CraftPlayer) player).getHandle().onGround()
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

        if (shallowWater
            && currentDirection.isMoving()
            && this.stepPermits.isFallbackReady(playerId)) {
            final StepTransitionPermitTracker.LandingAttempt fallbackAttempt =
                this.stepPermits.inspectLanding(
                    playerId,
                    worldId,
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    currentDirection,
                    stepHeight(player)
                );
            if (fallbackAttempt.acceptedForFallback()) {
                final StepTransitionPermitTracker.Landing landing =
                    fallbackAttempt.landing();
                final Location landingLocation = new Location(
                    player.getWorld(),
                    landing.targetX(),
                    landing.targetY(),
                    landing.targetZ(),
                    origin.getYaw(),
                    origin.getPitch()
                );
                final Location predictedOrigin = new Location(
                    player.getWorld(),
                    landing.anchorX(),
                    landing.baselineY(),
                    landing.anchorZ(),
                    origin.getYaw(),
                    origin.getPitch()
                );
                if (this.stepDetector.isLegitimateStep(
                    player,
                    predictedOrigin,
                    landingLocation
                )) {
                    final LandingMotionDecision motionDecision =
                        this.selectLandingMotion(player, landing, false);
                    this.stepPermits.complete(playerId);
                    this.motionSuppressor.teleportWithMotion(
                        player,
                        landingLocation,
                        motionDecision.motion()
                    );
                    this.forgetSuppressionState(playerId);
                    return true;
                }
            } else if (fallbackAttempt.status()
                == StepTransitionPermitTracker.LandingStatus.ABOVE_TARGET) {
                this.stepPermits.complete(playerId);
                return false;
            }
        }

        if (this.stepPermits.isArmedExpired(playerId)) {
            final HorizontalCollisionProbe.MovementDirection direction =
                this.stepPermits.direction(playerId);
            this.sendMotionReset(player, direction);
            this.stepPermits.blockExpired(
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
        if (!this.confirmedSuppression.matchesWorld(playerId, worldId)) {
            this.forgetMovementState(playerId);
            return;
        }
        if (!this.confirmedSuppression.recordAirborneRecoveryTick(
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

    private void schedulePostLandingRestore(
        final Player player,
        final StepTransitionPermitTracker.Landing landing
    ) {
        final UUID playerId = player.getUniqueId();
        final int token = this.landingRestoreTokens.merge(
            playerId,
            1,
            Integer::sum
        );
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            final Integer currentToken = this.landingRestoreTokens.get(playerId);
            if (currentToken == null || currentToken != token) {
                return;
            }
            this.landingRestoreTokens.remove(playerId, token);
            this.restoreLandingMotion(player, landing);
        });
    }

    private void restoreLandingMotion(
        final Player player,
        final StepTransitionPermitTracker.Landing landing
    ) {
        final LandingMotionDecision decision = this.selectLandingMotion(
            player,
            landing,
            true
        );
        if (decision.send()) {
            this.motionSuppressor.clearUpwardMotion(
                player,
                decision.motion()
            );
        }
    }

    private LandingMotionDecision selectLandingMotion(
        final Player player,
        final StepTransitionPermitTracker.Landing landing,
        final boolean requireLandingPosition
    ) {
        final ClientHorizontalMotionTracker.HorizontalMotion retainedMotion =
            landing.retainedMotion();
        final double retainedSpeed = retainedMotion.speed();
        if (!Double.isFinite(retainedSpeed)
            || retainedSpeed <= MOTION_RESTORE_EPSILON) {
            return LandingMotionDecision.rejected();
        }
        if (!player.isOnline() || !isEligibleMovementState(player)) {
            return LandingMotionDecision.rejected();
        }
        if (this.recentActivity.hasRecentJumpInput(
            player.getUniqueId(),
            Bukkit.getCurrentTick()
        ) || this.recentActivity.hasRecentExternalVelocity(
            player.getUniqueId(),
            Bukkit.getCurrentTick()
        )) {
            return LandingMotionDecision.rejected();
        }

        final Location current = player.getLocation();
        final double deltaX = current.getX() - landing.targetX();
        final double deltaY = current.getY() - landing.targetY();
        final double deltaZ = current.getZ() - landing.targetZ();
        final double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (current.getWorld() == null
            || !current.getWorld().getUID().equals(landing.worldId())
            || (requireLandingPosition
                && (!Double.isFinite(horizontalDistanceSquared)
                    || horizontalDistanceSquared
                        > MAX_MOTION_RESTORE_HORIZONTAL_DISTANCE_SQUARED
                    || !Double.isFinite(deltaY)
                    || Math.abs(deltaY)
                        > MAX_MOTION_RESTORE_VERTICAL_DISTANCE))) {
            return LandingMotionDecision.rejected();
        }

        final Input input = player.getCurrentInput();
        final HorizontalCollisionProbe.MovementDirection direction =
            HorizontalCollisionProbe.movementDirection(
                current.getYaw(),
                input
            );
        if (input.isJump()
            || !direction.isMoving()
            || landing.direction().dot(direction)
                < MIN_MOTION_RESTORE_DIRECTION_DOT) {
            return LandingMotionDecision.rejected();
        }
        return new LandingMotionDecision(retainedMotion, true);
    }

    private record LandingMotionDecision(
        ClientHorizontalMotionTracker.HorizontalMotion motion,
        boolean send
    ) {
        private static LandingMotionDecision rejected() {
            return new LandingMotionDecision(
                ClientHorizontalMotionTracker.HorizontalMotion.ZERO,
                false
            );
        }
    }

    private static double stepHeight(final Player player) {
        final AttributeInstance attribute = player.getAttribute(
            Attribute.STEP_HEIGHT
        );
        return attribute == null ? Double.NaN : attribute.getValue();
    }

    private void blockStepAttempt(
        final UUID playerId,
        final UUID worldId,
        final Location origin,
        final HorizontalCollisionProbe.MovementDirection direction,
        final StepTransitionPermitTracker.BlockReason reason
    ) {
        switch (reason) {
            case UNWANTED_JUMP -> this.stepPermits.blockUnwantedJump(
                playerId,
                worldId,
                origin.getX(),
                origin.getZ(),
                direction
            );
            case PERMIT_EXPIRED -> this.stepPermits.blockExpired(
                playerId,
                worldId,
                origin.getX(),
                origin.getZ(),
                direction
            );
            case PERMIT_REJECTED -> this.stepPermits.blockRejected(
                playerId,
                worldId,
                origin.getX(),
                origin.getZ(),
                direction
            );
        }
    }

    private void forgetMovementState(final UUID playerId) {
        this.forgetSuppressionState(playerId);
        this.stepPermits.forget(playerId);
    }

    private void forgetSuppressionState(final UUID playerId) {
        this.confirmedSuppression.forget(playerId);
        this.recentWallContact.forget(playerId);
        this.stableStepCandidates.forget(playerId);
    }

    static Location selectRollbackLocation(
        final Player player,
        final Location originalEventFrom,
        final Location current,
        final Location requested
    ) {
        final Location rollback = current.clone();
        if (originalEventFrom.getWorld() == current.getWorld()
            && Double.isFinite(originalEventFrom.getY())) {
            rollback.setY(originalEventFrom.getY());
            if (player.collidesAt(rollback)) {
                rollback.setX(originalEventFrom.getX());
                rollback.setZ(originalEventFrom.getZ());
            }
        }

        final double deltaX = requested.getX() - rollback.getX();
        final double deltaZ = requested.getZ() - rollback.getZ();
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

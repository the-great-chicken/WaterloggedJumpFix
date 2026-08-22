package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepTransitionPermitTrackerTest {
    private final StepTransitionPermitTracker tracker =
        new StepTransitionPermitTracker();
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final HorizontalCollisionProbe.MovementDirection direction =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 1.0D);
    private final ProjectedStepSimulator.StepResult step =
        new ProjectedStepSimulator.StepResult(
            true,
            0.02D,
            0.5D,
            0.0D,
            0.02D
        );

    @Test
    void consumesARecentlyArmedMatchingLanding() {
        this.arm();

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.02D,
                this.direction,
                0.6D
            );

        assertTrue(attempt.accepted());
        assertNotNull(attempt.landing());
        assertEquals(10.0D, attempt.landing().targetX());
        assertEquals(101.0D, attempt.landing().targetY());
        assertEquals(20.02D, attempt.landing().targetZ());
        assertEquals(0.02D, attempt.horizontalDrift(), 1.0E-9D);
        assertEquals(0.0D, attempt.verticalDrift(), 1.0E-9D);
        assertEquals(1.0D, attempt.directionDot(), 1.0E-9D);
        assertEquals(
            StepTransitionPermitTracker.ArmSource.MOTION,
            attempt.landing().source()
        );
        assertEquals(0.08D, attempt.landing().retainedMotion().z());
        assertFalse(this.tracker.isArmed(this.playerId));
    }

    @Test
    void fallbackInspectionWaitsOneTickAndDoesNotConsumeThePermit() {
        this.arm();
        assertFalse(this.tracker.isFallbackReady(this.playerId));

        this.tracker.advanceClientTick(this.playerId);

        assertTrue(this.tracker.isFallbackReady(this.playerId));
        final StepTransitionPermitTracker.LandingAttempt inspected =
            this.tracker.inspectLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.02D,
                this.direction,
                0.6D
            );
        assertTrue(inspected.accepted());
        assertTrue(this.tracker.isArmed(this.playerId));
        assertTrue(this.consume().accepted());
    }

    @Test
    void landingCanOnlyBeConsumedOnce() {
        this.arm();
        assertTrue(this.consume().accepted());

        final StepTransitionPermitTracker.LandingAttempt second = this.consume();

        assertFalse(second.hadPermit());
        assertEquals(
            StepTransitionPermitTracker.LandingStatus.NONE,
            second.status()
        );
    }

    @Test
    void armedPermitExpiresAfterItsShortPacketWindow() {
        this.arm();
        for (int tick = 0;
            tick <= StepTransitionPermitTracker.MAX_ARMED_CLIENT_TICKS;
            tick++) {
            this.tracker.advanceClientTick(this.playerId);
        }

        assertTrue(this.tracker.isArmedExpired(this.playerId));
        assertEquals(
            StepTransitionPermitTracker.LandingStatus.EXPIRED,
            this.consume().status()
        );
    }

    @Test
    void recognizesSupportedArrivalAtThePredictedHeight() {
        this.arm();

        assertTrue(this.tracker.hasReachedTarget(this.playerId, 101.0D, true));
        assertFalse(this.tracker.hasReachedTarget(this.playerId, 100.8D, true));
        assertFalse(this.tracker.hasReachedTarget(this.playerId, 101.0D, false));
    }

    @Test
    void rejectedAttemptCannotImmediatelyRearmAtTheSamePosition() {
        this.tracker.blockUnwantedJump(
            this.playerId,
            this.worldId,
            10.0D,
            20.0D,
            this.direction
        );

        assertTrue(
            this.tracker.isRearmBlocked(
                this.playerId,
                this.worldId,
                10.02D,
                20.0D,
                this.direction
            )
        );
        assertFalse(
            this.tracker.isRearmBlocked(
                this.playerId,
                this.worldId,
                10.2D,
                20.0D,
                this.direction
            )
        );
    }

    @Test
    void releasingInputClearsARejectedAttempt() {
        this.tracker.blockUnwantedJump(
            this.playerId,
            this.worldId,
            10.0D,
            20.0D,
            this.direction
        );
        final var noDirection =
            new HorizontalCollisionProbe.MovementDirection(0.0D, 0.0D);

        assertFalse(
            this.tracker.isRearmBlocked(
                this.playerId,
                this.worldId,
                10.0D,
                20.0D,
                noDirection
            )
        );
        assertFalse(
            this.tracker.isRearmBlocked(
                this.playerId,
                this.worldId,
                10.0D,
                20.0D,
                this.direction
            )
        );
    }

    @Test
    void landingRejectsAChangedDirection() {
        this.arm();
        final var oppositeDirection =
            new HorizontalCollisionProbe.MovementDirection(0.0D, -1.0D);

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                oppositeDirection,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.DIRECTION_MISMATCH,
            attempt.status()
        );
        assertFalse(this.tracker.isArmed(this.playerId));
    }

    @Test
    void landingRejectsExcessiveOriginDrift() {
        this.arm();

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.4D,
                100.5D,
                20.0D,
                this.direction,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.ORIGIN_DRIFTED,
            attempt.status()
        );
    }

    @Test
    void landingAcceptsProgressInsideThePredictedVerticalCorridor() {
        final var shallowStep = new ProjectedStepSimulator.StepResult(
            true,
            0.02D,
            0.25D,
            0.0D,
            0.02D
        );
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            shallowStep,
            new ClientHorizontalMotionTracker.HorizontalMotion(0.0D, 0.08D),
            StepTransitionPermitTracker.ArmSource.MOTION
        );

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.73053D,
                20.02D,
                this.direction,
                0.6D
            );

        assertTrue(attempt.accepted());
        assertEquals(0.23053D, attempt.verticalDrift(), 1.0E-9D);
        assertEquals(
            0.01947D,
            attempt.landing().targetY()
                - attempt.landing().baselineY()
                - attempt.verticalDrift(),
            1.0E-9D
        );
    }

    @Test
    void fallbackAcceptsAConservativeCrossedTargetOvershoot() {
        final var shallowStep = new ProjectedStepSimulator.StepResult(
            true,
            0.02D,
            0.25D,
            0.0D,
            0.02D
        );
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            shallowStep,
            new ClientHorizontalMotionTracker.HorizontalMotion(0.0D, 0.08D),
            StepTransitionPermitTracker.ArmSource.MOTION
        );

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.781D,
                20.02D,
                this.direction,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.CROSSED_TARGET,
            attempt.status()
        );
        assertFalse(attempt.accepted());
        assertTrue(attempt.acceptedForFallback());
        assertEquals(0.031D, attempt.targetVerticalOffset(), 1.0E-9D);
    }

    @Test
    void fallbackReleasesMovementWellAboveThePredictedTarget() {
        final var shallowStep = new ProjectedStepSimulator.StepResult(
            true,
            0.02D,
            0.25D,
            0.0D,
            0.02D
        );
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            shallowStep,
            new ClientHorizontalMotionTracker.HorizontalMotion(0.0D, 0.08D),
            StepTransitionPermitTracker.ArmSource.MOTION
        );

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.inspectLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.831D,
                20.02D,
                this.direction,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.ABOVE_TARGET,
            attempt.status()
        );
        assertFalse(attempt.acceptedForFallback());
        assertEquals(0.081D, attempt.targetVerticalOffset(), 1.0E-9D);
    }

    @Test
    void landingAcceptsSettlingBelowStaleBaselineWhenTargetRemainsReachable() {
        final var settlingStep = new ProjectedStepSimulator.StepResult(
            true,
            0.025D,
            0.25D,
            0.0D,
            0.025D
        );
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            200.76637D,
            20.0D,
            this.direction,
            settlingStep,
            new ClientHorizontalMotionTracker.HorizontalMotion(0.0D, 0.08D),
            StepTransitionPermitTracker.ArmSource.MOTION
        );

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                200.53584D,
                20.025D,
                this.direction,
                0.6D
            );

        assertTrue(attempt.accepted());
        assertEquals(-0.23053D, attempt.verticalDrift(), 1.0E-9D);
        assertEquals(
            0.48053D,
            attempt.landing().targetY() - 200.53584D,
            1.0E-9D
        );
    }

    @Test
    void landingRejectsTargetBeyondCurrentStepHeight() {
        this.arm();

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                this.worldId,
                10.0D,
                100.349D,
                20.0D,
                this.direction,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.ORIGIN_DRIFTED,
            attempt.status()
        );
    }

    @Test
    void landingRejectsAChangedWorld() {
        this.arm();

        final StepTransitionPermitTracker.LandingAttempt attempt =
            this.tracker.consumeLanding(
                this.playerId,
                UUID.randomUUID(),
                10.0D,
                100.5D,
                20.0D,
                this.direction,
                0.6D
            );

        assertEquals(
            StepTransitionPermitTracker.LandingStatus.WORLD_MISMATCH,
            attempt.status()
        );
    }

    private StepTransitionPermitTracker.LandingAttempt consume() {
        return this.tracker.consumeLanding(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            0.6D
        );
    }

    private void arm() {
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            this.step,
            new ClientHorizontalMotionTracker.HorizontalMotion(0.0D, 0.08D),
            StepTransitionPermitTracker.ArmSource.MOTION
        );
    }

}

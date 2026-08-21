package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void activatesARecentlyArmedMatchingTransition() {
        this.arm();

        assertTrue(
            this.tracker.activate(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.02D,
                this.direction
            )
        );
        assertTrue(this.tracker.isActive(this.playerId));
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
        assertFalse(
            this.tracker.activate(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                this.direction
            )
        );
    }

    @Test
    void activePermitExpiresAfterItsLandingWindow() {
        this.arm();
        assertTrue(
            this.tracker.activate(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                this.direction
            )
        );
        for (int tick = 0;
            tick <= StepTransitionPermitTracker.MAX_ACTIVE_CLIENT_TICKS;
            tick++) {
            this.tracker.advanceClientTick(this.playerId);
        }

        assertTrue(this.tracker.isActiveExpired(this.playerId));
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
        this.tracker.block(
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
        this.tracker.block(
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
    void activationRejectsAChangedDirection() {
        this.arm();
        final var oppositeDirection =
            new HorizontalCollisionProbe.MovementDirection(0.0D, -1.0D);

        assertFalse(
            this.tracker.activate(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                oppositeDirection
            )
        );
        assertFalse(this.tracker.isArmed(this.playerId));
    }

    private void arm() {
        this.tracker.arm(
            this.playerId,
            this.worldId,
            10.0D,
            100.5D,
            20.0D,
            this.direction,
            this.step
        );
    }
}

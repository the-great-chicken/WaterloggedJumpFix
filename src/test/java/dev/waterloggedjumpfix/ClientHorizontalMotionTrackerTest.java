package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientHorizontalMotionTrackerTest {
    private static final double TOLERANCE = 1.0E-12D;

    private final ClientHorizontalMotionTracker tracker =
        new ClientHorizontalMotionTracker();
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();

    @Test
    void firstObservationHasNoEstimatedMotion() {
        final ClientHorizontalMotionTracker.HorizontalMotion motion =
            this.tracker.observe(this.playerId, this.worldId, 10.0D, 20.0D);

        assertMotion(0.0D, 0.0D, motion);
    }

    @Test
    void preservesAndDampsHorizontalTickDisplacement() {
        this.tracker.observe(this.playerId, this.worldId, 10.0D, 20.0D);

        final ClientHorizontalMotionTracker.HorizontalMotion motion = this.tracker
            .observe(this.playerId, this.worldId, 10.1D, 19.8D)
            .damped(0.8D);

        assertMotion(0.08D, -0.16D, motion);
        assertMotion(0.1D, -0.2D, this.tracker.latest(this.playerId));
        assertEquals(Math.hypot(0.1D, -0.2D), motion.speed() / 0.8D, TOLERANCE);
    }

    @Test
    void removesOnlyComponentsThatOpposeCurrentInput() {
        final var motion = new ClientHorizontalMotionTracker.HorizontalMotion(
            -0.2D,
            0.3D
        );
        final var direction = new HorizontalCollisionProbe.MovementDirection(
            1.0D,
            0.0D
        );

        assertMotion(
            0.0D,
            0.3D,
            motion.withoutOpposingComponents(direction)
        );
    }

    @Test
    void preservesSlidingThatDoesNotOpposeCurrentInput() {
        final var motion = new ClientHorizontalMotionTracker.HorizontalMotion(
            0.05D,
            0.2D
        );
        final var direction = new HorizontalCollisionProbe.MovementDirection(
            0.0D,
            1.0D
        );

        assertMotion(
            0.05D,
            0.2D,
            motion.withoutOpposingComponents(direction)
        );
    }

    @Test
    void retainsTheFasterOfTheTwoMostRecentCompatibleMotions() {
        this.tracker.observe(this.playerId, this.worldId, 0.0D, 0.0D);
        this.tracker.observe(this.playerId, this.worldId, 0.18D, 0.04D);
        this.tracker.observe(this.playerId, this.worldId, 0.19D, 0.05D);
        final var direction = new HorizontalCollisionProbe.MovementDirection(
            1.0D,
            0.0D
        );

        assertMotion(
            0.18D,
            0.04D,
            this.tracker.bestRecent(this.playerId, direction)
        );
    }

    @Test
    void retainedMotionStillDropsComponentsOpposingCurrentInput() {
        this.tracker.observe(this.playerId, this.worldId, 0.0D, 0.0D);
        this.tracker.observe(this.playerId, this.worldId, -0.18D, 0.04D);
        this.tracker.observe(this.playerId, this.worldId, -0.19D, 0.05D);
        final var direction = new HorizontalCollisionProbe.MovementDirection(
            1.0D,
            0.0D
        );

        assertMotion(
            0.0D,
            0.04D,
            this.tracker.bestRecent(this.playerId, direction)
        );
    }

    @Test
    void retainedMotionDoesNotReverseTheLatestTangentialMovement() {
        this.tracker.observe(this.playerId, this.worldId, 0.0D, 0.0D);
        this.tracker.observe(this.playerId, this.worldId, 0.18D, 0.04D);
        this.tracker.observe(this.playerId, this.worldId, 0.17D, 0.05D);
        final var direction = new HorizontalCollisionProbe.MovementDirection(
            0.0D,
            1.0D
        );

        assertMotion(
            -0.01D,
            0.01D,
            this.tracker.bestRecent(this.playerId, direction)
        );
    }

    @Test
    void worldChangeDoesNotBecomeVelocity() {
        this.tracker.observe(this.playerId, this.worldId, 10.0D, 20.0D);

        final ClientHorizontalMotionTracker.HorizontalMotion motion =
            this.tracker.observe(this.playerId, UUID.randomUUID(), 2.0D, 3.0D);

        assertMotion(0.0D, 0.0D, motion);
        assertMotion(
            0.0D,
            0.0D,
            this.tracker.bestRecent(
                this.playerId,
                new HorizontalCollisionProbe.MovementDirection(1.0D, 0.0D)
            )
        );
    }

    @Test
    void implausiblyLargeDisplacementDoesNotBecomeVelocity() {
        this.tracker.observe(this.playerId, this.worldId, 0.0D, 0.0D);

        final ClientHorizontalMotionTracker.HorizontalMotion motion =
            this.tracker.observe(this.playerId, this.worldId, 2.0D, 0.0D);

        assertMotion(0.0D, 0.0D, motion);
    }

    @Test
    void forgettingPlayerResetsTheSample() {
        this.tracker.observe(this.playerId, this.worldId, 0.0D, 0.0D);
        this.tracker.forget(this.playerId);

        assertMotion(0.0D, 0.0D, this.tracker.latest(this.playerId));

        final ClientHorizontalMotionTracker.HorizontalMotion motion =
            this.tracker.observe(this.playerId, this.worldId, 0.1D, 0.0D);

        assertMotion(0.0D, 0.0D, motion);
    }

    private static void assertMotion(
        final double expectedX,
        final double expectedZ,
        final ClientHorizontalMotionTracker.HorizontalMotion actual
    ) {
        assertEquals(expectedX, actual.x(), TOLERANCE);
        assertEquals(expectedZ, actual.z(), TOLERANCE);
    }
}

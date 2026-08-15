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
    }

    @Test
    void worldChangeDoesNotBecomeVelocity() {
        this.tracker.observe(this.playerId, this.worldId, 10.0D, 20.0D);

        final ClientHorizontalMotionTracker.HorizontalMotion motion =
            this.tracker.observe(this.playerId, UUID.randomUUID(), 2.0D, 3.0D);

        assertMotion(0.0D, 0.0D, motion);
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

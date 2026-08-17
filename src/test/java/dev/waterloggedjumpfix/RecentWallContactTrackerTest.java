package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecentWallContactTrackerTest {
    private final RecentWallContactTracker tracker = new RecentWallContactTracker();
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final HorizontalCollisionProbe.MovementDirection forward =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 1.0D);
    private final HorizontalCollisionProbe.MovementDirection none =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 0.0D);

    @Test
    void currentInputAlwaysWins() {
        final RecentWallContactTracker.DirectionResolution resolution =
            this.tracker.resolve(this.playerId, this.worldId, this.forward);

        assertTrue(resolution.isAvailable());
        assertEquals(this.forward, resolution.direction());
        assertEquals(0, resolution.ageClientTicks());
    }

    @Test
    void retainsContactAcrossExactlyTwoReleasedInputTicks() {
        this.tracker.recordContact(this.playerId, this.worldId, this.forward);

        for (int tick = 0;
            tick <= RecentWallContactTracker.RELEASE_GRACE_CLIENT_TICKS;
            tick++) {
            assertTrue(
                this.tracker.resolve(this.playerId, this.worldId, this.none)
                    .isAvailable()
            );
            this.tracker.advanceClientTick(this.playerId);
        }

        assertFalse(
            this.tracker.resolve(this.playerId, this.worldId, this.none)
                .isAvailable()
        );
    }

    @Test
    void recentContactDoesNotCrossWorlds() {
        this.tracker.recordContact(this.playerId, this.worldId, this.forward);

        assertFalse(
            this.tracker.resolve(this.playerId, UUID.randomUUID(), this.none)
                .isAvailable()
        );
    }
}

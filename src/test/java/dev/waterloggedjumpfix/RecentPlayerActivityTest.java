package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecentPlayerActivityTest {
    private final RecentPlayerActivity activity = new RecentPlayerActivity();
    private final UUID playerId = UUID.randomUUID();

    @Test
    void jumpInputUsesTwoTickGraceWindow() {
        this.activity.recordJumpInput(this.playerId, 100);

        assertTrue(this.activity.hasRecentJumpInput(this.playerId, 100));
        assertTrue(this.activity.hasRecentJumpInput(this.playerId, 102));
        assertFalse(this.activity.hasRecentJumpInput(this.playerId, 103));
    }

    @Test
    void externalVelocityUsesThreeTickGraceWindow() {
        this.activity.recordExternalVelocity(this.playerId, 200);

        assertTrue(this.activity.hasRecentExternalVelocity(this.playerId, 200));
        assertTrue(this.activity.hasRecentExternalVelocity(this.playerId, 203));
        assertFalse(this.activity.hasRecentExternalVelocity(this.playerId, 204));
    }

    @Test
    void forgettingPlayerRemovesBothActivities() {
        this.activity.recordJumpInput(this.playerId, 300);
        this.activity.recordExternalVelocity(this.playerId, 300);

        this.activity.forget(this.playerId);

        assertFalse(this.activity.hasRecentJumpInput(this.playerId, 300));
        assertFalse(this.activity.hasRecentExternalVelocity(this.playerId, 300));
    }

    @Test
    void clearingTrackerRemovesBothActivities() {
        this.activity.recordJumpInput(this.playerId, 400);
        this.activity.recordExternalVelocity(this.playerId, 400);

        this.activity.clear();

        assertFalse(this.activity.hasRecentJumpInput(this.playerId, 400));
        assertFalse(this.activity.hasRecentExternalVelocity(this.playerId, 400));
    }

    @Test
    void tickCounterWrapKeepsShortGraceWindowsWorking() {
        this.activity.recordJumpInput(this.playerId, Integer.MAX_VALUE);

        assertTrue(this.activity.hasRecentJumpInput(this.playerId, Integer.MIN_VALUE));
        assertTrue(this.activity.hasRecentJumpInput(this.playerId, Integer.MIN_VALUE + 1));
        assertFalse(this.activity.hasRecentJumpInput(this.playerId, Integer.MIN_VALUE + 2));
    }
}

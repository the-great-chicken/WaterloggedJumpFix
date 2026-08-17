package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmedSuppressionTest {
    private static final double BASELINE_Y = 100.5D;
    private final ConfirmedSuppression suppression = new ConfirmedSuppression();
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final HorizontalCollisionProbe.MovementDirection forward =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 1.0D);

    @Test
    void confirmationPersistsUntilForgotten() {
        this.confirm();

        assertTrue(this.suppression.isConfirmed(this.playerId));
        assertEquals(BASELINE_Y, this.suppression.baselineY(this.playerId));

        this.suppression.forget(this.playerId);
        assertFalse(this.suppression.isConfirmed(this.playerId));
    }

    @Test
    void clearRemovesEveryConfirmation() {
        this.confirm();
        this.suppression.confirm(
            UUID.randomUUID(),
            this.worldId,
            this.forward,
            BASELINE_Y
        );

        this.suppression.clear();

        assertFalse(this.suppression.isConfirmed(this.playerId));
    }

    @Test
    void toleratesExactlyTwoConsecutiveProbeMisses() {
        this.confirm();

        assertTrue(this.suppression.recordProbeMiss(this.playerId));
        assertTrue(this.suppression.recordProbeMiss(this.playerId));
        assertFalse(this.suppression.recordProbeMiss(this.playerId));
    }

    @Test
    void confirmationRestartsTheProbeGraceWindow() {
        this.confirm();
        assertTrue(this.suppression.recordProbeMiss(this.playerId));
        assertTrue(this.suppression.recordProbeMiss(this.playerId));

        this.confirm();

        assertTrue(this.suppression.recordProbeMiss(this.playerId));
        assertTrue(this.suppression.recordProbeMiss(this.playerId));
    }

    @Test
    void repeatedConfirmationTracksDirectionWithoutRebasingUpwards() {
        this.confirm();
        final HorizontalCollisionProbe.MovementDirection sideways =
            new HorizontalCollisionProbe.MovementDirection(1.0D, 0.0D);

        this.suppression.confirm(
            this.playerId,
            this.worldId,
            sideways,
            BASELINE_Y + 0.3D
        );

        assertEquals(BASELINE_Y, this.suppression.baselineY(this.playerId));
        assertEquals(sideways, this.suppression.direction(this.playerId));
    }

    @Test
    void airborneRecoveryRequiresARecentBoundedRise() {
        this.confirm();

        for (int tick = 0;
            tick < ConfirmedSuppression.MAX_AIRBORNE_RECOVERY_CLIENT_TICKS;
            tick++) {
            assertTrue(
                this.suppression.recordAirborneRecoveryTick(
                    this.playerId,
                    this.worldId,
                    BASELINE_Y + 0.3D
                )
            );
        }
        assertFalse(
            this.suppression.recordAirborneRecoveryTick(
                this.playerId,
                this.worldId,
                BASELINE_Y + 0.3D
            )
        );
    }

    @Test
    void airborneRecoveryRejectsBaselineAndExcessiveRise() {
        this.confirm();

        assertFalse(
            this.suppression.recordAirborneRecoveryTick(
                this.playerId,
                this.worldId,
                BASELINE_Y
            )
        );
        assertFalse(
            this.suppression.recordAirborneRecoveryTick(
                this.playerId,
                this.worldId,
                BASELINE_Y + ConfirmedSuppression.MAX_AIRBORNE_RECOVERY_RISE
                    + 0.01D
            )
        );
    }

    private void confirm() {
        this.suppression.confirm(
            this.playerId,
            this.worldId,
            this.forward,
            BASELINE_Y
        );
    }
}

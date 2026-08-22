package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StableStepCandidateTrackerTest {
    private final StableStepCandidateTracker tracker =
        new StableStepCandidateTracker();
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final HorizontalCollisionProbe.MovementDirection direction =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 1.0D);

    @Test
    void confirmsRepeatedObservationsOfTheSameAbsoluteTarget() {
        assertEquals(
            1,
            this.observe(10.0D, 20.0D, 0.05D)
        );
        assertEquals(
            2,
            this.observe(10.02D, 20.02D, 0.03D)
        );
        assertEquals(
            2,
            this.observe(10.03D, 20.03D, 0.02D)
        );
    }

    @Test
    void aDifferentTargetRestartsConfirmation() {
        assertEquals(1, this.observe(10.0D, 20.0D, 0.05D));

        assertEquals(1, this.observe(10.2D, 20.0D, 0.05D));
    }

    @Test
    void aDifferentWorldRestartsConfirmation() {
        assertEquals(1, this.observe(10.0D, 20.0D, 0.05D));

        assertEquals(
            1,
            this.tracker.observe(
                this.playerId,
                UUID.randomUUID(),
                10.0D,
                100.5D,
                20.0D,
                this.direction,
                step(0.05D)
            )
        );
    }

    @Test
    void aChangedDirectionRestartsConfirmation() {
        assertEquals(1, this.observe(10.0D, 20.0D, 0.05D));
        final var opposite =
            new HorizontalCollisionProbe.MovementDirection(0.0D, -1.0D);

        assertEquals(
            1,
            this.tracker.observe(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                opposite,
                step(0.05D)
            )
        );
    }

    @Test
    void anInvalidObservationClearsConfirmation() {
        assertEquals(1, this.observe(10.0D, 20.0D, 0.05D));
        assertEquals(
            0,
            this.tracker.observe(
                this.playerId,
                this.worldId,
                10.0D,
                100.5D,
                20.0D,
                this.direction,
                new ProjectedStepSimulator.StepResult(
                    false,
                    Double.NaN,
                    Double.NaN,
                    0.0D,
                    0.0D
                )
            )
        );
        assertEquals(1, this.observe(10.0D, 20.0D, 0.05D));
    }

    private int observe(
        final double originX,
        final double originZ,
        final double offsetZ
    ) {
        return this.tracker.observe(
            this.playerId,
            this.worldId,
            originX,
            100.5D,
            originZ,
            this.direction,
            step(offsetZ)
        );
    }

    private static ProjectedStepSimulator.StepResult step(
        final double offsetZ
    ) {
        return new ProjectedStepSimulator.StepResult(
            true,
            Math.abs(offsetZ),
            0.5D,
            0.0D,
            offsetZ
        );
    }
}

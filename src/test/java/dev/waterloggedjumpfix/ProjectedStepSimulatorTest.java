package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectedStepSimulatorTest {
    private static final HorizontalCollisionProbe.MovementDirection FORWARD =
        new HorizontalCollisionProbe.MovementDirection(0.0D, 1.0D);
    private static final HorizontalCollisionProbe.MovementDirection DIAGONAL =
        new HorizontalCollisionProbe.MovementDirection(
            Math.sqrt(0.5D),
            Math.sqrt(0.5D)
        );

    @Test
    void rejectsAnObstacleHigherThanThePlayersStepHeight() {
        final ProjectedStepSimulator.StepResult result =
            ProjectedStepSimulator.simulate(
                FORWARD,
                0.2D,
                0.6D,
                (x, y, z) -> z >= 0.05D && y < 1.0D
            );

        assertFalse(result.stepable());
    }

    @Test
    void acceptsAnImmediatelyReachableHalfBlockStep() {
        final ProjectedStepSimulator.StepResult result =
            ProjectedStepSimulator.simulate(
                FORWARD,
                0.2D,
                0.6D,
                (x, y, z) -> z >= 0.05D && y < 0.5D
            );

        assertTrue(result.stepable());
    }

    @Test
    void findsAStepAfterSlidingPastAnAdjacentTallWall() {
        final ProjectedStepSimulator.StepResult result =
            ProjectedStepSimulator.simulate(
                DIAGONAL,
                0.4D,
                0.6D,
                (x, y, z) -> z >= 0.05D
                    && y < (x < 0.1D ? 1.0D : 0.5D)
            );

        assertTrue(result.stepable());
    }
}

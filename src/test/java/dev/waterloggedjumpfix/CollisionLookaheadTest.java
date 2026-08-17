package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CollisionLookaheadTest {
    private static final double TOLERANCE = 1.0E-12D;
    private final CollisionLookahead lookahead = new CollisionLookahead();

    @Test
    void addsOneObservedTickDistancePerFiftyMilliseconds() {
        assertEquals(
            0.34D,
            this.lookahead.distance(
                0.1D,
                0.6D,
                40,
                new ClientHorizontalMotionTracker.HorizontalMotion(0.3D, 0.0D)
            ),
            TOLERANCE
        );
    }

    @Test
    void honorsConfiguredCap() {
        assertEquals(
            0.6D,
            this.lookahead.distance(
                0.1D,
                0.6D,
                200,
                new ClientHorizontalMotionTracker.HorizontalMotion(0.4D, 0.0D)
            ),
            TOLERANCE
        );
    }
}

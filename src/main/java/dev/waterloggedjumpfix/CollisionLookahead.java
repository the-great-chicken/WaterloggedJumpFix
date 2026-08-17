package dev.waterloggedjumpfix;

/** Converts recent movement and round-trip latency into a swept probe distance. */
final class CollisionLookahead {
    private static final double CLIENT_TICK_MILLIS = 50.0D;

    double distance(
        final double baseDistance,
        final double maximumDistance,
        final int pingMillis,
        final ClientHorizontalMotionTracker.HorizontalMotion motion
    ) {
        final double pingClientTicks = Math.max(0, pingMillis)
            / CLIENT_TICK_MILLIS;
        final double latencyDistance = motion.speed() * pingClientTicks;
        return Math.min(maximumDistance, baseDistance + latencyDistance);
    }
}

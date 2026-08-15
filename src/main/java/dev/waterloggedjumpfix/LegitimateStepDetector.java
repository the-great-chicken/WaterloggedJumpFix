package dev.waterloggedjumpfix;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** Recognizes a collision-supported step that must not be treated as a jump. */
final class LegitimateStepDetector {
    private static final double DISPLACEMENT_EPSILON = 1.0E-5D;
    private static final double SUPPORT_PROBE_DEPTH = 0.05D;

    boolean isLegitimateStep(
        final Player player,
        final Location origin,
        final Location requested
    ) {
        if (origin.getWorld() != requested.getWorld()) {
            return false;
        }

        final AttributeInstance stepHeightAttribute = player.getAttribute(
            Attribute.STEP_HEIGHT
        );
        if (stepHeightAttribute == null) {
            return false;
        }

        final double deltaX = requested.getX() - origin.getX();
        final double rise = requested.getY() - origin.getY();
        final double deltaZ = requested.getZ() - origin.getZ();
        final double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (!hasPlausibleStepDisplacement(
            rise,
            stepHeightAttribute.getValue(),
            horizontalDistanceSquared
        )) {
            return false;
        }

        if (player.collidesAt(requested)) {
            return false;
        }

        final Location supportProbe = requested.clone().subtract(
            0.0D,
            SUPPORT_PROBE_DEPTH,
            0.0D
        );
        if (!player.collidesAt(supportProbe)) {
            return false;
        }

        final Location unsteppedDestination = origin.clone();
        unsteppedDestination.setX(requested.getX());
        unsteppedDestination.setZ(requested.getZ());
        return player.collidesAt(unsteppedDestination);
    }

    static boolean hasPlausibleStepDisplacement(
        final double rise,
        final double stepHeight,
        final double horizontalDistanceSquared
    ) {
        return Double.isFinite(rise)
            && Double.isFinite(stepHeight)
            && Double.isFinite(horizontalDistanceSquared)
            && rise > DISPLACEMENT_EPSILON
            && stepHeight >= 0.0D
            && rise <= stepHeight + DISPLACEMENT_EPSILON
            && horizontalDistanceSquared > DISPLACEMENT_EPSILON * DISPLACEMENT_EPSILON;
    }
}

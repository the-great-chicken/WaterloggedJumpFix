package dev.waterloggedjumpfix;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Simulates a swept horizontal move in small pieces so a legal step reached
 * while sliding along a taller wall is not hidden by the wall collision.
 */
final class ProjectedStepSimulator {
    private static final double HORIZONTAL_SAMPLE_DISTANCE = 0.025D;
    private static final double VERTICAL_SAMPLE_DISTANCE = 0.025D;
    private static final double SUPPORT_PROBE_DEPTH = 0.02D;

    StepResult assess(
        final Player player,
        final Location origin,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double distance
    ) {
        final AttributeInstance stepHeightAttribute = player.getAttribute(
            Attribute.STEP_HEIGHT
        );
        if (stepHeightAttribute == null || origin.getWorld() == null) {
            return StepResult.NONE;
        }

        return simulate(
            direction,
            distance,
            stepHeightAttribute.getValue(),
            (x, y, z) -> player.collidesAt(
                origin.clone().add(x, y, z)
            )
        );
    }

    static StepResult simulate(
        final HorizontalCollisionProbe.MovementDirection direction,
        final double distance,
        final double stepHeight,
        final CollisionView collisions
    ) {
        if (!direction.isMoving()
            || !Double.isFinite(distance)
            || distance <= 0.0D
            || !Double.isFinite(stepHeight)
            || stepHeight <= 0.0D) {
            return StepResult.NONE;
        }

        final int samples = Math.max(
            1,
            (int) Math.ceil(distance / HORIZONTAL_SAMPLE_DISTANCE)
        );
        final double deltaX = direction.x() * distance / samples;
        final double deltaZ = direction.z() * distance / samples;
        double offsetX = 0.0D;
        double offsetZ = 0.0D;

        for (int sample = 1; sample <= samples; sample++) {
            final double desiredX = offsetX + deltaX;
            final double desiredZ = offsetZ + deltaZ;
            if (!collisions.collides(desiredX, 0.0D, desiredZ)) {
                offsetX = desiredX;
                offsetZ = desiredZ;
                continue;
            }

            final double directRise = supportedRise(
                collisions,
                desiredX,
                desiredZ,
                stepHeight
            );
            if (Double.isFinite(directRise)) {
                return new StepResult(
                    true,
                    Math.hypot(desiredX, desiredZ),
                    directRise,
                    desiredX,
                    desiredZ
                );
            }

            if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
                final StepResult xStep = stepOnAxis(
                    collisions,
                    offsetX + deltaX,
                    offsetZ,
                    stepHeight
                );
                if (xStep.stepable()) {
                    return xStep;
                }
                if (!collisions.collides(offsetX + deltaX, 0.0D, offsetZ)) {
                    offsetX += deltaX;
                }
                final StepResult zStep = stepOnAxis(
                    collisions,
                    offsetX,
                    offsetZ + deltaZ,
                    stepHeight
                );
                if (zStep.stepable()) {
                    return zStep;
                }
                if (!collisions.collides(offsetX, 0.0D, offsetZ + deltaZ)) {
                    offsetZ += deltaZ;
                }
            } else {
                final StepResult zStep = stepOnAxis(
                    collisions,
                    offsetX,
                    offsetZ + deltaZ,
                    stepHeight
                );
                if (zStep.stepable()) {
                    return zStep;
                }
                if (!collisions.collides(offsetX, 0.0D, offsetZ + deltaZ)) {
                    offsetZ += deltaZ;
                }
                final StepResult xStep = stepOnAxis(
                    collisions,
                    offsetX + deltaX,
                    offsetZ,
                    stepHeight
                );
                if (xStep.stepable()) {
                    return xStep;
                }
                if (!collisions.collides(offsetX + deltaX, 0.0D, offsetZ)) {
                    offsetX += deltaX;
                }
            }
        }

        return StepResult.NONE;
    }

    private static StepResult stepOnAxis(
        final CollisionView collisions,
        final double candidateX,
        final double candidateZ,
        final double stepHeight
    ) {
        if (!collisions.collides(candidateX, 0.0D, candidateZ)) {
            return StepResult.NONE;
        }
        final double rise = supportedRise(
            collisions,
            candidateX,
            candidateZ,
            stepHeight
        );
        return Double.isFinite(rise)
            ? new StepResult(
                true,
                Math.hypot(candidateX, candidateZ),
                rise,
                candidateX,
                candidateZ
            )
            : StepResult.NONE;
    }

    private static double supportedRise(
        final CollisionView collisions,
        final double x,
        final double z,
        final double stepHeight
    ) {
        final int samples = Math.max(
            1,
            (int) Math.ceil(stepHeight / VERTICAL_SAMPLE_DISTANCE)
        );
        for (int sample = 1; sample <= samples; sample++) {
            final double rise = Math.min(
                stepHeight,
                sample * VERTICAL_SAMPLE_DISTANCE
            );
            if (!collisions.collides(x, rise, z)
                && collisions.collides(
                    x,
                    Math.max(0.0D, rise - SUPPORT_PROBE_DEPTH),
                    z
                )) {
                return rise;
            }
        }
        return Double.NaN;
    }

    @FunctionalInterface
    interface CollisionView {
        boolean collides(double x, double y, double z);
    }

    record StepResult(
        boolean stepable,
        double distance,
        double rise,
        double offsetX,
        double offsetZ
    ) {
        private static final StepResult NONE = new StepResult(
            false,
            Double.NaN,
            Double.NaN,
            0.0D,
            0.0D
        );
    }
}

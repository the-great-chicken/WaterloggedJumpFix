package dev.waterloggedjumpfix;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Uses vanilla's collision resolver to recognize a step along a projected path. */
final class VanillaStepSimulator {
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0E-10D;
    private static final double STEP_EPSILON = 1.0E-5D;

    private final MethodHandle collideHandle;

    VanillaStepSimulator() {
        try {
            this.collideHandle = MethodHandles.privateLookupIn(
                Entity.class,
                MethodHandles.lookup()
            ).findVirtual(
                Entity.class,
                "collide",
                MethodType.methodType(Vec3.class, Vec3.class)
            );
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "Paper's vanilla collision resolver is unavailable",
                exception
            );
        }
    }

    StepResult assess(
        final Player player,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double distance
    ) {
        if (!direction.isMoving()
            || !Double.isFinite(distance)
            || distance <= 0.0D) {
            return StepResult.NONE;
        }

        final double intendedX = direction.x() * distance;
        final double intendedZ = direction.z() * distance;
        try {
            final Vec3 intended = new Vec3(
                intendedX,
                0.0D,
                intendedZ
            );
            final Vec3 resolved = (Vec3) this.collideHandle.invoke(
                ((CraftPlayer) player).getHandle(),
                intended
            );
            return fromResolvedMovement(
                resolved.x,
                resolved.y,
                resolved.z
            );
        } catch (final Throwable throwable) {
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                "Could not simulate vanilla step movement",
                throwable
            );
        }
    }

    static StepResult fromResolvedMovement(
        final double resolvedX,
        final double resolvedY,
        final double resolvedZ
    ) {
        final double horizontalDistanceSquared = resolvedX * resolvedX
            + resolvedZ * resolvedZ;
        final boolean horizontalMovement = horizontalDistanceSquared
            > MOVEMENT_EPSILON_SQUARED;
        final boolean stepable = horizontalMovement && resolvedY > STEP_EPSILON;
        return new StepResult(
            stepable,
            resolvedX,
            resolvedY,
            resolvedZ
        );
    }

    record StepResult(
        boolean stepable,
        double resolvedX,
        double resolvedY,
        double resolvedZ
    ) {
        private static final StepResult NONE = new StepResult(
            false,
            0.0D,
            0.0D,
            0.0D
        );
    }
}

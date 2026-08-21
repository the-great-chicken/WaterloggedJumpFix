package dev.waterloggedjumpfix;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Uses vanilla's collision resolver to recognize a step along a projected path. */
final class VanillaStepSimulator {
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0E-10D;
    private static final double STEP_EPSILON = 1.0E-5D;

    private final Constructor<?> vectorConstructor;
    private final MethodHandle collideHandle;
    private final Field vectorX;
    private final Field vectorY;
    private final Field vectorZ;
    private Method getHandleMethod;

    VanillaStepSimulator() {
        try {
            final ClassLoader classLoader = VanillaStepSimulator.class
                .getClassLoader();
            final Class<?> vectorClass = Class.forName(
                "net.minecraft.world.phys.Vec3",
                false,
                classLoader
            );
            final Class<?> entityClass = Class.forName(
                "net.minecraft.world.entity.Entity",
                false,
                classLoader
            );
            this.vectorConstructor = vectorClass.getConstructor(
                double.class,
                double.class,
                double.class
            );
            this.collideHandle = MethodHandles.privateLookupIn(
                entityClass,
                MethodHandles.lookup()
            ).findVirtual(
                entityClass,
                "collide",
                MethodType.methodType(vectorClass, vectorClass)
            );
            this.vectorX = vectorClass.getField("x");
            this.vectorY = vectorClass.getField("y");
            this.vectorZ = vectorClass.getField("z");
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
            final Object intended = this.vectorConstructor.newInstance(
                intendedX,
                0.0D,
                intendedZ
            );
            final Object handle = this.handleFor(player);
            final Object resolved = this.collideHandle.invoke(handle, intended);
            return fromResolvedMovement(
                intendedX,
                intendedZ,
                this.vectorX.getDouble(resolved),
                this.vectorY.getDouble(resolved),
                this.vectorZ.getDouble(resolved)
            );
        } catch (final Throwable throwable) {
            if (throwable instanceof Error error) {
                throw error;
            }
            final Throwable cause = throwable instanceof InvocationTargetException
                && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            throw new IllegalStateException(
                "Could not simulate vanilla step movement",
                cause
            );
        }
    }

    private Object handleFor(final Player player)
        throws ReflectiveOperationException {
        if (this.getHandleMethod == null
            || !this.getHandleMethod.getDeclaringClass().isInstance(player)) {
            this.getHandleMethod = player.getClass().getMethod("getHandle");
            if (!this.getHandleMethod.trySetAccessible()) {
                throw new IllegalAccessException(
                    "Paper's player handle is inaccessible"
                );
            }
        }
        return this.getHandleMethod.invoke(player);
    }

    static StepResult fromResolvedMovement(
        final double intendedX,
        final double intendedZ,
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
            intendedX,
            intendedZ,
            resolvedX,
            resolvedY,
            resolvedZ
        );
    }

    record StepResult(
        boolean stepable,
        double intendedX,
        double intendedZ,
        double resolvedX,
        double resolvedY,
        double resolvedZ
    ) {
        private static final StepResult NONE = new StepResult(
            false,
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0.0D
        );
    }
}

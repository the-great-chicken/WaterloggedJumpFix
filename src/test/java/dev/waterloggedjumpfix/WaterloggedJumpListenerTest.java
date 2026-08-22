package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class WaterloggedJumpListenerTest {
    @Test
    void unwantedJumpRollbackUsesTheOriginalEventHeight() {
        final Location eventFrom = new Location(null, 10.0D, 100.5D, 20.0D);
        final Location current = new Location(null, 10.02D, 101.1D, 20.04D);
        final Location requested = new Location(
            null,
            10.03D,
            101.4D,
            20.08D,
            30.0F,
            10.0F
        );

        final Location rollback = WaterloggedJumpListener.selectRollbackLocation(
            player(false),
            eventFrom,
            current,
            requested
        );

        assertEquals(requested.getX(), rollback.getX());
        assertEquals(eventFrom.getY(), rollback.getY());
        assertEquals(requested.getZ(), rollback.getZ());
        assertEquals(requested.getYaw(), rollback.getYaw());
        assertEquals(requested.getPitch(), rollback.getPitch());
        assertEquals(101.1D, current.getY());
    }

    @Test
    void collidingHorizontalRollbackFallsBackToTheOriginalEventPosition() {
        final Location eventFrom = new Location(null, 10.0D, 100.5D, 20.0D);
        final Location current = new Location(null, 10.02D, 101.1D, 20.04D);
        final Location requested = new Location(null, 10.03D, 101.4D, 20.08D);

        final Location rollback = WaterloggedJumpListener.selectRollbackLocation(
            player(true),
            eventFrom,
            current,
            requested
        );

        assertEquals(eventFrom.getX(), rollback.getX());
        assertEquals(eventFrom.getY(), rollback.getY());
        assertEquals(eventFrom.getZ(), rollback.getZ());
    }

    private static Player player(final boolean collides) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "collidesAt" -> collides;
                case "toString" -> "TestPlayer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(
                    method.toString()
                );
            }
        );
    }
}

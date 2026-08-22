package dev.waterloggedjumpfix;

import dev.waterloggedjumpfix.ClientHorizontalMotionTracker.HorizontalMotion;
import java.util.Collections;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Sends a self-only motion update that clears Y without discarding X/Z. */
final class ClientMotionSuppressor {
    void clearUpwardMotion(final Player player, final HorizontalMotion motion) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send(
            new ClientboundSetEntityMotionPacket(
                handle.getId(),
                new Vec3(motion.x(), 0.0D, motion.z())
            )
        );
    }

    void teleportWithMotion(
        final Player player,
        final Location destination,
        final HorizontalMotion motion
    ) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.internalTeleport(
            new PositionMoveRotation(
                new Vec3(
                    destination.getX(),
                    destination.getY(),
                    destination.getZ()
                ),
                new Vec3(motion.x(), 0.0D, motion.z()),
                destination.getYaw(),
                destination.getPitch()
            ),
            Collections.emptySet()
        );
    }
}

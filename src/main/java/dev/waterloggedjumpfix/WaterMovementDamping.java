package dev.waterloggedjumpfix;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Reproduces the client water-friction factor for retained X/Z velocity. */
final class WaterMovementDamping {
    private static final double DEFAULT_WATER_SLOWDOWN = 0.8D;
    private static final double SPRINTING_WATER_SLOWDOWN = 0.9D;
    private static final double WATER_EFFICIENT_TARGET = 0.546000063419342D;
    private static final double DOLPHINS_GRACE_SLOWDOWN = 0.9599999785423279D;

    double forPlayer(final Player player) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        return calculate(
            player.isSprinting(),
            handle.onGround(),
            handle.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY),
            handle.hasEffect(MobEffects.DOLPHINS_GRACE)
        );
    }

    static double calculate(
        final boolean sprinting,
        final boolean onGround,
        final double waterMovementEfficiency,
        final boolean hasDolphinsGrace
    ) {
        if (hasDolphinsGrace) {
            return DOLPHINS_GRACE_SLOWDOWN;
        }

        double damping = sprinting
            ? SPRINTING_WATER_SLOWDOWN
            : DEFAULT_WATER_SLOWDOWN;
        double efficiency = waterMovementEfficiency;
        if (!onGround) {
            efficiency *= 0.5D;
        }
        if (efficiency > 0.0D) {
            damping += (WATER_EFFICIENT_TARGET - damping) * efficiency;
        }
        return damping;
    }
}

package dev.waterloggedjumpfix;

import org.bukkit.entity.Player;

/** Detects water contact while the player's eyes remain above the surface. */
final class ShallowWaterContactDetector {
    boolean isInShallowWater(final Player player) {
        return isShallowWater(player.isInWater(), player.isUnderWater());
    }

    static boolean isShallowWater(
        final boolean bodyInWater,
        final boolean eyesInWater
    ) {
        return bodyInWater && !eyesInWater;
    }
}

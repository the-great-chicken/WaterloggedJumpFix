package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps short-lived intent and external-motion exemptions without scheduling a
 * task for every player action.
 */
final class RecentPlayerActivity {
    static final int JUMP_INPUT_GRACE_TICKS = 2;
    static final int EXTERNAL_VELOCITY_GRACE_TICKS = 3;

    private final Map<UUID, Integer> jumpInputTicks = new HashMap<>();
    private final Map<UUID, Integer> externalVelocityTicks = new HashMap<>();

    void recordJumpInput(final UUID playerId, final int currentTick) {
        this.jumpInputTicks.put(playerId, currentTick);
    }

    boolean hasRecentJumpInput(final UUID playerId, final int currentTick) {
        return isRecent(
            this.jumpInputTicks,
            playerId,
            currentTick,
            JUMP_INPUT_GRACE_TICKS
        );
    }

    void recordExternalVelocity(final UUID playerId, final int currentTick) {
        this.externalVelocityTicks.put(playerId, currentTick);
    }

    boolean hasRecentExternalVelocity(final UUID playerId, final int currentTick) {
        return isRecent(
            this.externalVelocityTicks,
            playerId,
            currentTick,
            EXTERNAL_VELOCITY_GRACE_TICKS
        );
    }

    void forget(final UUID playerId) {
        this.jumpInputTicks.remove(playerId);
        this.externalVelocityTicks.remove(playerId);
    }

    void clear() {
        this.jumpInputTicks.clear();
        this.externalVelocityTicks.clear();
    }

    private static boolean isRecent(
        final Map<UUID, Integer> activityTicks,
        final UUID playerId,
        final int currentTick,
        final int graceTicks
    ) {
        final Integer activityTick = activityTicks.get(playerId);
        if (activityTick == null) {
            return false;
        }

        // Signed subtraction also handles the normal wrap from MAX_VALUE to
        // MIN_VALUE, as long as the measured interval remains short.
        final int age = currentTick - activityTick;
        if (age >= 0 && age <= graceTicks) {
            return true;
        }

        if (age > graceTicks) {
            activityTicks.remove(playerId, activityTick);
        }
        return false;
    }
}

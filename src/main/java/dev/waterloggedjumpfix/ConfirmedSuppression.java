package dev.waterloggedjumpfix;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Remembers players whose unwanted boost was confirmed by a cancelled jump. */
final class ConfirmedSuppression {
    private final Set<UUID> playerIds = new HashSet<>();

    void confirm(final UUID playerId) {
        this.playerIds.add(playerId);
    }

    boolean isConfirmed(final UUID playerId) {
        return this.playerIds.contains(playerId);
    }

    void forget(final UUID playerId) {
        this.playerIds.remove(playerId);
    }

    void clear() {
        this.playerIds.clear();
    }
}

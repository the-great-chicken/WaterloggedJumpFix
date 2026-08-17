package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks a confirmed wall contact, its safe Y, and short recovery windows. */
final class ConfirmedSuppression {
    static final int MAX_CONSECUTIVE_PROBE_MISSES = 2;
    static final int MAX_AIRBORNE_RECOVERY_CLIENT_TICKS = 6;
    static final double MAX_AIRBORNE_RECOVERY_RISE = 1.25D;
    static final double VERTICAL_GUARD_EPSILON = 0.05D;
    private final Map<UUID, Contact> contacts = new HashMap<>();

    void confirm(
        final UUID playerId,
        final UUID worldId,
        final HorizontalCollisionProbe.MovementDirection direction,
        final double baselineY
    ) {
        if (!direction.isMoving() || !Double.isFinite(baselineY)) {
            this.forget(playerId);
            return;
        }

        final Contact current = this.contacts.get(playerId);
        if (current != null && current.worldId.equals(worldId)) {
            current.direction = direction;
            current.baselineY = Math.min(current.baselineY, baselineY);
            current.consecutiveProbeMisses = 0;
            current.airborneRecoveryClientTicks = 0;
            return;
        }

        this.contacts.put(playerId, new Contact(worldId, direction, baselineY));
    }

    boolean isConfirmed(final UUID playerId) {
        return this.contacts.containsKey(playerId);
    }

    boolean matchesWorld(final UUID playerId, final UUID worldId) {
        final Contact contact = this.contacts.get(playerId);
        return contact != null && contact.worldId.equals(worldId);
    }

    double baselineY(final UUID playerId) {
        final Contact contact = this.contacts.get(playerId);
        return contact == null ? Double.NaN : contact.baselineY;
    }

    HorizontalCollisionProbe.MovementDirection direction(final UUID playerId) {
        final Contact contact = this.contacts.get(playerId);
        return contact == null
            ? new HorizontalCollisionProbe.MovementDirection(0.0D, 0.0D)
            : contact.direction;
    }

    boolean recordProbeMiss(final UUID playerId) {
        final Contact contact = this.contacts.get(playerId);
        if (contact == null) {
            return false;
        }

        contact.consecutiveProbeMisses++;
        return contact.consecutiveProbeMisses <= MAX_CONSECUTIVE_PROBE_MISSES;
    }

    boolean recordAirborneRecoveryTick(
        final UUID playerId,
        final UUID worldId,
        final double currentY
    ) {
        final Contact contact = this.contacts.get(playerId);
        if (contact == null
            || !contact.worldId.equals(worldId)
            || !Double.isFinite(currentY)) {
            return false;
        }

        final double rise = currentY - contact.baselineY;
        if (rise <= VERTICAL_GUARD_EPSILON
            || rise > MAX_AIRBORNE_RECOVERY_RISE) {
            return false;
        }

        contact.airborneRecoveryClientTicks++;
        return contact.airborneRecoveryClientTicks
            <= MAX_AIRBORNE_RECOVERY_CLIENT_TICKS;
    }

    void forget(final UUID playerId) {
        this.contacts.remove(playerId);
    }

    void clear() {
        this.contacts.clear();
    }

    private static final class Contact {
        private final UUID worldId;
        private HorizontalCollisionProbe.MovementDirection direction;
        private double baselineY;
        private int consecutiveProbeMisses;
        private int airborneRecoveryClientTicks;

        private Contact(
            final UUID worldId,
            final HorizontalCollisionProbe.MovementDirection direction,
            final double baselineY
        ) {
            this.worldId = worldId;
            this.direction = direction;
            this.baselineY = baselineY;
        }
    }
}

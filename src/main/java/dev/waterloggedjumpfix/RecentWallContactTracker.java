package dev.waterloggedjumpfix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Retains an observed wall direction across a very short input-release gap. */
final class RecentWallContactTracker {
    static final int RELEASE_GRACE_CLIENT_TICKS = 2;
    private final Map<UUID, Contact> contacts = new HashMap<>();

    void advanceClientTick(final UUID playerId) {
        final Contact contact = this.contacts.get(playerId);
        if (contact == null) {
            return;
        }

        contact.ageClientTicks++;
        if (contact.ageClientTicks > RELEASE_GRACE_CLIENT_TICKS) {
            this.contacts.remove(playerId);
        }
    }

    void recordContact(
        final UUID playerId,
        final UUID worldId,
        final HorizontalCollisionProbe.MovementDirection direction
    ) {
        if (!direction.isMoving()) {
            this.forget(playerId);
            return;
        }
        this.contacts.put(playerId, new Contact(worldId, direction));
    }

    DirectionResolution resolve(
        final UUID playerId,
        final UUID worldId,
        final HorizontalCollisionProbe.MovementDirection currentDirection
    ) {
        if (currentDirection.isMoving()) {
            return new DirectionResolution(currentDirection, 0);
        }

        final Contact contact = this.contacts.get(playerId);
        if (contact == null || !contact.worldId.equals(worldId)) {
            return DirectionResolution.NONE;
        }
        return new DirectionResolution(contact.direction, contact.ageClientTicks);
    }

    void forget(final UUID playerId) {
        this.contacts.remove(playerId);
    }

    void clear() {
        this.contacts.clear();
    }

    record DirectionResolution(
        HorizontalCollisionProbe.MovementDirection direction,
        int ageClientTicks
    ) {
        private static final DirectionResolution NONE = new DirectionResolution(
            new HorizontalCollisionProbe.MovementDirection(0.0D, 0.0D),
            -1
        );

        boolean isAvailable() {
            return this.direction.isMoving();
        }
    }

    private static final class Contact {
        private final UUID worldId;
        private final HorizontalCollisionProbe.MovementDirection direction;
        private int ageClientTicks;

        private Contact(
            final UUID worldId,
            final HorizontalCollisionProbe.MovementDirection direction
        ) {
            this.worldId = worldId;
            this.direction = direction;
        }
    }
}

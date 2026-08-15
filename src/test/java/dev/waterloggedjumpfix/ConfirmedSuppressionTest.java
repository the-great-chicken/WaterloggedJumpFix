package dev.waterloggedjumpfix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmedSuppressionTest {
    private final ConfirmedSuppression suppression = new ConfirmedSuppression();
    private final UUID playerId = UUID.randomUUID();

    @Test
    void confirmationPersistsUntilForgotten() {
        this.suppression.confirm(this.playerId);

        assertTrue(this.suppression.isConfirmed(this.playerId));

        this.suppression.forget(this.playerId);
        assertFalse(this.suppression.isConfirmed(this.playerId));
    }

    @Test
    void clearRemovesEveryConfirmation() {
        this.suppression.confirm(this.playerId);
        this.suppression.confirm(UUID.randomUUID());

        this.suppression.clear();

        assertFalse(this.suppression.isConfirmed(this.playerId));
    }
}

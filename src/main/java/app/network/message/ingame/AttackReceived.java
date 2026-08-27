package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record AttackReceived(UUID attackerId, String attackerName, boolean hit, boolean critical, int damage,
        int currentHealth, int maxHealth, boolean defeated) implements OutputJsonMessage {

}

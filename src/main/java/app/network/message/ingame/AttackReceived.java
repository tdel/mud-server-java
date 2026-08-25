package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AttackReceived(String attackerName, boolean hit, boolean critical, int damage, int currentHealth,
        int maxHealth, boolean defeated) implements OutputJsonMessage {

}

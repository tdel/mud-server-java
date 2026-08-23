package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AttackReceived(String attackerName, boolean hit, boolean critical, int damage, int currentHealth,
        int maxHealth, boolean defeated) implements OutputJsonMessage {

}

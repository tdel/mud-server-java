package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AttackResult(String targetName, boolean hit, boolean critical, int damage, int targetCurrentHealth,
        int targetMaxHealth, boolean targetDefeated) implements OutputJsonMessage {

}

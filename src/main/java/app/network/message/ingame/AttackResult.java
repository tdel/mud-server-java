package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AttackResult(String targetName, boolean hit, boolean critical, int damage, int targetCurrentHealth,
        int targetMaxHealth, boolean targetDefeated) implements OutputJsonMessage {

}

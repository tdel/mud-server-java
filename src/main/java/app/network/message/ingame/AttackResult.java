package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record AttackResult(UUID targetId, String targetName, boolean hit, boolean critical, int damage,
        int targetCurrentHealth, int targetMaxHealth, boolean targetDefeated) implements OutputJsonMessage {

}

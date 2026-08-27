package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CastResult(UUID spellId, String spellName, UUID targetId, String targetName, boolean selfHeal,
        boolean hit, int amount, int targetCurrentHealth, int targetMaxHealth,
        boolean targetDefeated) implements OutputJsonMessage {

}

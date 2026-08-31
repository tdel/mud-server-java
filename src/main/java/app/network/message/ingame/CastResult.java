package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CastResult(UUID skillId, String skillName, UUID targetId, String targetName, boolean selfHeal,
        boolean hit, int amount, int targetCurrentHealth, int targetMaxHealth, boolean targetDefeated, int manaSpent,
        int casterCurrentMana, int casterMaxMana) implements OutputJsonMessage {

}

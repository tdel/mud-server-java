package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CastResult(String spellName, String targetName, boolean selfHeal, boolean hit, int amount,
        int targetCurrentHealth, int targetMaxHealth, boolean targetDefeated) implements OutputJsonMessage {

}

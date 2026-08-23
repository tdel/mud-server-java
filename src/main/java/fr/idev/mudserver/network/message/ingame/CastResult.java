package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record CastResult(String spellName, String targetName, boolean selfHeal, boolean hit, int amount,
        int targetCurrentHealth, int targetMaxHealth, boolean targetDefeated) implements OutputJsonMessage {

}

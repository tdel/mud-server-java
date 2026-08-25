package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellCastObserved(String casterName, String spellName, String targetName, boolean selfHeal, boolean hit,
        int amount, boolean targetDefeated) implements OutputJsonMessage {

}

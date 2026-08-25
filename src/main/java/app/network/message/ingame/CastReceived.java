package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CastReceived(String casterName, String spellName, boolean hit, int amount, int currentHealth,
        int maxHealth, boolean defeated) implements OutputJsonMessage {

}

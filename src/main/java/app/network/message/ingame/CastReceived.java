package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record CastReceived(UUID casterId, String casterName, UUID spellId, String spellName, boolean hit, int amount,
        int currentHealth, int maxHealth, boolean defeated) implements OutputJsonMessage {

}

package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record CastReceived(String casterName, String spellName, boolean hit, int amount, int currentHealth,
        int maxHealth, boolean defeated) implements OutputJsonMessage {

}

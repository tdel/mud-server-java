package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberVitalsUpdated(UUID characterId, String characterName, int currentHealth, int maxHealth,
        int currentMana, int maxMana) implements OutputJsonMessage {

}

package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Un joueur à portée revient à la vie sur place (Resurrection).
public record CharacterResurrected(UUID characterId, String characterName, int currentHealth,
        int maxHealth) implements OutputJsonMessage {
}

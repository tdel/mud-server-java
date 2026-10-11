package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Shadow Step : le personnage apparaît instantanément à (x, y), sans trajet.
public record CharacterBlinked(UUID characterId, String characterName, double x, double y,
        double heading) implements OutputJsonMessage {
}

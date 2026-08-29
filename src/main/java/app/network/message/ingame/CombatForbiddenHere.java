package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record CombatForbiddenHere(String zoneName) implements OutputJsonMessage {

}

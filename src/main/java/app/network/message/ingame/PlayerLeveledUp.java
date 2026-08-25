package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PlayerLeveledUp(String characterName, int newLevel) implements OutputJsonMessage {

}

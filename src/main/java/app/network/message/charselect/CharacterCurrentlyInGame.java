package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record CharacterCurrentlyInGame(String name) implements OutputJsonMessage {

}

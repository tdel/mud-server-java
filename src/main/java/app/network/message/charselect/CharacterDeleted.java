package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record CharacterDeleted(String name) implements OutputJsonMessage {

}

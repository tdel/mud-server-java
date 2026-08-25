package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record CharacterNameTaken(String name) implements OutputJsonMessage {

}

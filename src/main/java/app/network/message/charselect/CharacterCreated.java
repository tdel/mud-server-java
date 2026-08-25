package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record CharacterCreated(String name) implements OutputJsonMessage {

}

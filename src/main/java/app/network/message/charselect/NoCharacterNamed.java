package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record NoCharacterNamed(String name) implements OutputJsonMessage {

}

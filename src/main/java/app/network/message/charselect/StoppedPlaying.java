package app.network.message.charselect;

import app.network.OutputJsonMessage;

public record StoppedPlaying(String characterName) implements OutputJsonMessage {

}

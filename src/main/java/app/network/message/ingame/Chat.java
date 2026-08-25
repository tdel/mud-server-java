package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record Chat(String speakerName, String text) implements OutputJsonMessage {

}

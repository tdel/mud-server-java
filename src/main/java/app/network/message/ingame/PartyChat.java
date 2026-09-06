package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PartyChat(String speakerName, String text) implements OutputJsonMessage {

}

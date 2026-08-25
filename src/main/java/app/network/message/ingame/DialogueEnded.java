package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record DialogueEnded(String npcName) implements OutputJsonMessage {

}

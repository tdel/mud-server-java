package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record DialogueEnded(UUID npcId, String npcName) implements OutputJsonMessage {

}

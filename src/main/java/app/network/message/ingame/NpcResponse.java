package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record NpcResponse(UUID npcId, String npcName, String response) implements OutputJsonMessage {

}

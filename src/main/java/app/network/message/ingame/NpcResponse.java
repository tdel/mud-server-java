package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NpcResponse(String npcName, String response) implements OutputJsonMessage {

}

package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record NpcResponse(String npcName, String response) implements OutputJsonMessage {

}

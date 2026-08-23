package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record PlayerRespawned(String zoneName) implements OutputJsonMessage {

}

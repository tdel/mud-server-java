package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record ItemNotFound(String name) implements OutputJsonMessage {

}

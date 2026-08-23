package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AttackOutOfRange(String targetName) implements OutputJsonMessage {

}

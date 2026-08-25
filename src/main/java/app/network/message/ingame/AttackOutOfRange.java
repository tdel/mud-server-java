package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AttackOutOfRange(String targetName) implements OutputJsonMessage {

}

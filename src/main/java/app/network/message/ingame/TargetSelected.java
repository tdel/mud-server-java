package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record TargetSelected(String monsterName) implements OutputJsonMessage {

}

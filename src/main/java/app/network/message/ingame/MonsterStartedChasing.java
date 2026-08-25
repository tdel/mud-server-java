package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MonsterStartedChasing(String monsterName) implements OutputJsonMessage {

}

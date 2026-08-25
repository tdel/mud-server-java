package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MonsterDefeated(String monsterName) implements OutputJsonMessage {

}

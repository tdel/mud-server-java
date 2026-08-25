package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record MonsterGaveUpChase(String monsterName) implements OutputJsonMessage {

}

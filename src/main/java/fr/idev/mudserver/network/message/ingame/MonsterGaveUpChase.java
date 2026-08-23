package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MonsterGaveUpChase(String monsterName) implements OutputJsonMessage {

}

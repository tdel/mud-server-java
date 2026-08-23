package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record MonsterDefeated(String monsterName) implements OutputJsonMessage {

}

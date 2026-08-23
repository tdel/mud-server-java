package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record PlayerLeveledUp(String characterName, int newLevel) implements OutputJsonMessage {

}

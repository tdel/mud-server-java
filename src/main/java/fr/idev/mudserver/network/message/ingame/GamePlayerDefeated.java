package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record GamePlayerDefeated(String characterName, String killerName) implements OutputJsonMessage {

}

package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record GamePlayerDefeated(String characterName, String killerName) implements OutputJsonMessage {

}

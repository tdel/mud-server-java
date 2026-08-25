package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellOutOfRange(String spellName, String targetName) implements OutputJsonMessage {

}

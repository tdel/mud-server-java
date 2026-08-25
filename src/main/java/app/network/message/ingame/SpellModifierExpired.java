package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellModifierExpired(String characterName, String spellName) implements OutputJsonMessage {

}

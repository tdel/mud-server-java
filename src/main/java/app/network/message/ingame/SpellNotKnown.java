package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellNotKnown(String spellId) implements OutputJsonMessage {

}

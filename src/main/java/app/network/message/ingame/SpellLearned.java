package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record SpellLearned(String spellName, int tier, boolean upgraded) implements OutputJsonMessage {

}

package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellFizzled(UUID spellId, String spellName, String reason) implements OutputJsonMessage {

}

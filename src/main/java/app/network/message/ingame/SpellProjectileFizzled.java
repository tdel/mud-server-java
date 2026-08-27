package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellProjectileFizzled(UUID projectileId, UUID spellId, String spellName) implements OutputJsonMessage {

}

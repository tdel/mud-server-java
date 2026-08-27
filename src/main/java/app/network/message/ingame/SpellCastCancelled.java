package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellCastCancelled(UUID casterId, String casterName, UUID spellId,
        String spellName) implements OutputJsonMessage {

}

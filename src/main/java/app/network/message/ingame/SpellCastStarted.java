package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record SpellCastStarted(UUID casterId, String casterName, UUID spellId, String spellName, UUID targetId,
        String targetName, int castingTimeMs) implements OutputJsonMessage {

}

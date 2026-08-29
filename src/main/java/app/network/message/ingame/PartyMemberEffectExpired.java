package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberEffectExpired(UUID characterId, String characterName,
        String spellName) implements OutputJsonMessage {

}

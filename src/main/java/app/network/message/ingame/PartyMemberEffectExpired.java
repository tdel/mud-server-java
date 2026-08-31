package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberEffectExpired(UUID characterId, String characterName,
        String skillName) implements OutputJsonMessage {

}

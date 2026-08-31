package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberEffectApplied(UUID characterId, String characterName, String skillName, String stat,
        int amount, long secondsRemaining) implements OutputJsonMessage {

}

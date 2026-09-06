package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PvpFlagChanged(UUID characterId, String characterName, boolean flagged) implements OutputJsonMessage {
}

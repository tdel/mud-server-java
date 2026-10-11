package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Le serviteur du joueur est parti (mort, durée écoulée, unsummon...).
public record SummonDismissed(UUID summonId, String name, String reason) implements OutputJsonMessage {
}

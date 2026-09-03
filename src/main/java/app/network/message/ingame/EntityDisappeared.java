package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Poussé quand une ou plusieurs entités sortent de la KnownList du personnage —
 * sortie de AWARENESS_RANGE (KnownList.refresh) ou départ définitif de la carte
 * (leave/disconnect/mort, KnownList.clear). Voir {@link EntityAppeared}.
 */
public record EntityDisappeared(List<UUID> entityIds) implements OutputJsonMessage {
}

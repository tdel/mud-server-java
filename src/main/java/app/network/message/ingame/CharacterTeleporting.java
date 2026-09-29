package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Fin de l'incantation de Teleport (Scroll of Escape) : le personnage,
 * désormais invulnérable, s'élève vers le ciel et sera téléporté vers
 * {@code destinationMapName} dans {@code delayMs} (cf. EscapeEngine). Diffusé
 * au lanceur comme aux joueurs alentour.
 */
public record CharacterTeleporting(UUID characterId, String characterName, String destinationMapName,
        int delayMs) implements OutputJsonMessage {

}

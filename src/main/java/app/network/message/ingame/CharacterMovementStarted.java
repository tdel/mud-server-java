package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé au reste de la zone quand un AUTRE personnage démarre un déplacement
 * (`goto`), pour que les clients puissent interpoler localement sa trajectoire
 * vers la cible plutôt que d'attendre une position poussée à chaque tick.
 */
public record CharacterMovementStarted(UUID characterId, String characterName, double targetX, double targetY,
        double heading) implements OutputJsonMessage {

}

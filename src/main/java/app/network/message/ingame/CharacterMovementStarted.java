package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Diffusé au reste de la zone quand un AUTRE personnage démarre un déplacement
 * (`goto`), pour que les clients puissent interpoler localement sa trajectoire
 * vers la cible plutôt que d'attendre une position poussée à chaque tick.
 */
public record CharacterMovementStarted(String characterName, double targetX, double targetY)
        implements OutputJsonMessage {

}

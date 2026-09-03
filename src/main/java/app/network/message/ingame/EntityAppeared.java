package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;

/**
 * Poussé quand une ou plusieurs entités (joueur, monstre, PNJ) entrent dans la
 * KnownList du personnage — au spawn (voir KnownList.populate) ou après un
 * déplacement (KnownList.refresh) qui les fait entrer dans AWARENESS_RANGE.
 * C'est, avec {@link EntityDisappeared}, le seul canal par lequel le client
 * apprend la présence d'une entité sur la carte (voir MapEnter).
 */
public record EntityAppeared(List<EntityView> entities) implements OutputJsonMessage {
}

package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Diffusé quand une entité (joueur, monstre ou PNJ) entre dans le rayon de
 * perception d'un personnage qui reste par ailleurs dans la même map (voir
 * KnownList.refresh) — distinct de GamePlayerJoinedMap/MonsterSpawned qui
 * couvrent un vrai changement de map/une réapparition.
 */
public record EntityAppeared(EntityKind kind, EntityView view) implements OutputJsonMessage {
}

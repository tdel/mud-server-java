package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé quand une entité sort du rayon de perception d'un personnage qui
 * reste par ailleurs dans la même zone (voir KnownList.refresh) — distinct de
 * GamePlayerLeftZone/GamePlayerDisconnected/MonsterDefeated qui couvrent un
 * vrai changement de zone/déconnexion/mort.
 */
public record EntityDisappeared(UUID id, EntityKind kind) implements OutputJsonMessage {
}

package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé à toute la zone quand un monstre réapparaît sur un point de spawn
 * libre (voir MonsterRespawnEngine) — jamais envoyé pour le placement initial
 * des monstres au démarrage du serveur, qui a lieu avant toute connexion de
 * joueur. Mêmes champs que ViewAround.EntityView (moins targetX/targetY : un
 * monstre qui vient d'apparaître est toujours immobile).
 */
public record MonsterSpawned(UUID id, String name, double x, double y, double speed, int currentHealth, int maxHealth,
        int level) implements OutputJsonMessage {
}

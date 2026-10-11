package app.domain.world;

import java.time.Instant;
import java.util.UUID;

import app.domain.map.Position;

// Cadavre d'un monstre tué, consommable quelques secondes par Corpse Life Drain
// / Life Scavenge (cf. MapInstance.nearestCorpse). Le monstre lui-même quitte
// la carte dès sa mort ; seul ce souvenir reste.
public record Corpse(UUID monsterId, String name, Position position, Instant expiresAt) {
}

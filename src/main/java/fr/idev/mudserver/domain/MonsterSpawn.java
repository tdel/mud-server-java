package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * Point de spawn d'un monstre, porté par le {@link RoomTemplate} qui le
 * contient (voir {@code data/worlds/{monde}/rooms.json}) plutôt que par
 * {@code data/monsters.json}, qui ne garde que les templates. Résolu depuis le
 * JSON par {@code WorldTemplateService}, copié sur chaque {@link RoomInstance}
 * matérialisée (voir {@code WorldInstanceService}), puis consommé par
 * {@code MonsterService.loadMonsters} pour instancier le
 * {@link fr.idev.mudserver.domain.actor.GameMonster} correspondant et le placer
 * via {@link RoomInstance#placeMonster}.
 */
public record MonsterSpawn(UUID id, UUID templateId, HexCoordinate cell) {
}

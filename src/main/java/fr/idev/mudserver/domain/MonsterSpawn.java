package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * Point de spawn d'un monstre, porté par la Room qui le contient (voir
 * {@code data/rooms.json}) plutôt que par {@code data/monsters.json}, qui ne
 * garde que les templates. Résolu depuis le JSON par
 * {@code RoomService.loadRooms}, puis consommé par
 * {@code MonsterService.loadMonsters} pour instancier le
 * {@link fr.idev.mudserver.domain.actor.GameMonster} correspondant et le placer
 * via {@link Room#placeMonster}.
 */
public record MonsterSpawn(UUID id, UUID templateId, HexCoordinate cell) {
}

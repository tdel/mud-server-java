package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#onEnteredCell} à chaque case franchie lors d'un
 * {@code moveToCell} (pas seulement à l'arrivée finale) : un déplacement
 * multi-cases (ex. {@code go nord 4}) ne doit pas pouvoir sauter par-dessus la
 * zone de présence d'un {@link fr.idev.mudserver.domain.actor.GameMonster} sans
 * jamais la détecter. Écouté par {@code game.CombatEngine} pour déclencher un
 * combat d'aggro.
 */
public record GamePlayerEnteredCell(GamePlayer character, HexCoordinate cell) {
}

package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GameMonster#takeDamage} lui-même quand les PV tombent à 0
 * (coup fatal) — avant toute autre mutation. Les effets de la mort du monstre
 * sont entièrement délégués aux listeners : {@code RoomService} le retire de sa
 * room et diffuse {@code MonsterDefeated}, {@code CharacterService} crédite
 * l'XP à {@code killer} et vide sa cible, {@code game.CombatEngine} le retire
 * de son {@code CombatEncounter} le cas échéant. Pendant côté monstre de
 * {@link GamePlayerDied}.
 */
public record CharacterDied(GameMonster character, GamePlayer killer) {
}

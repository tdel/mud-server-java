package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@code CombatService#tryAttack} juste après que
 * {@link GameMonster#takeDamage} a renvoyé {@code true} (coup fatal) — avant
 * toute autre mutation. Les effets de la mort du monstre sont entièrement
 * délégués aux listeners : {@code RoomService} le retire de sa room et diffuse
 * {@code MonsterDefeated}, {@code CharacterService} crédite l'XP à
 * {@code killer} et vide sa cible.
 */
public record CharacterDied(GameMonster character, GamePlayer killer) {
}

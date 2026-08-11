package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@code GamePlayer#takeDamage} quand les PV tombent à 0 — pendant
 * côté joueur de {@link CharacterDied}. {@code WorldInstanceService} diffuse
 * l'annonce à la room du mourant avant qu'il ne soit téléporté (@Order(1)),
 * {@code CharacterService} restaure ses PV, le téléporte à la starting room et
 * persiste (@Order(2)), {@code CombatEngine} le retire de son
 * {@code CombatEncounter} (sans ordre requis, effet indépendant).
 */
public record GamePlayerDied(GamePlayer character, GameMonster killer) {
}

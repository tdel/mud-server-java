package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#gainXp(int)} une fois {@code character.xp} déjà
 * incrémenté — un éventuel passage de palier de niveau n'est pas tranché ici
 * (ça dépend de {@code LevelService}, un bean Spring auquel {@link GamePlayer},
 * simple POJO, n'a pas accès), mais dans le listener de cet événement.
 */
public record CharacterGainedXp(GamePlayer character, int amount) {
}

package fr.idev.mudserver.domain.actor.event;

import java.util.Map;

import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@code game.actor.RestService#shortRest} une fois les PV de tous
 * les joueurs en ligne déjà soignés en mémoire et leur compteur de repos courts
 * incrémenté — {@code CharacterService} persiste chaque entrée de
 * {@code healedAmounts} et notifie chaque joueur affecté, sur le même principe
 * que {@link GamePlayerUsedPotion}. {@code initiator} n'est porté que pour
 * l'annonce ("X entame un repos court").
 */
public record ShortRestTaken(GamePlayer initiator, Map<GamePlayer, Integer> healedAmounts) {
}

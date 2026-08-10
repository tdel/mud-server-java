package fr.idev.mudserver.domain.actor.event;

import java.util.List;
import java.util.Map;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@code game.actor.RestService#longRest} une fois les PV de tous
 * les joueurs en ligne déjà restaurés au maximum en mémoire et leur compteur de
 * repos courts remis à zéro — deux listeners réagissent, même principe que
 * {@link GamePlayerUsedPotion} : {@code CharacterService} persiste chaque
 * entrée de {@code healedAmounts} et notifie chaque joueur affecté,
 * {@code ItemService} supprime les lignes DB de {@code consumedFood}
 * (provisions détruites par {@code initiator} pour financer ce repos).
 */
public record LongRestTaken(GamePlayer initiator, Map<GamePlayer, Integer> healedAmounts, List<Item> consumedFood) {
}

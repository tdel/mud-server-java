package fr.idev.mudserver.domain.actor.event;

import java.util.List;
import java.util.Map;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

public record LongRestTaken(GamePlayer initiator, Map<GamePlayer, Integer> healedAmounts, List<Item> consumedFood) {
}

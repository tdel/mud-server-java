package fr.idev.mudserver.domain.actor.event;

import java.util.List;
import java.util.Map;

import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record LongRestTaken(CharacterInstance initiator, Map<CharacterInstance, Integer> healedAmounts,
        List<Item> consumedFood) {
}

package fr.idev.mudserver.domain.actor.event;

import java.util.Map;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record ShortRestTaken(CharacterInstance initiator, Map<CharacterInstance, Integer> healedAmounts) {
}

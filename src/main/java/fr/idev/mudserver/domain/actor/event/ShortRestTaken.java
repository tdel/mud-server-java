package fr.idev.mudserver.domain.actor.event;

import java.util.Map;

import fr.idev.mudserver.domain.actor.GamePlayer;

public record ShortRestTaken(GamePlayer initiator, Map<GamePlayer, Integer> healedAmounts) {
}

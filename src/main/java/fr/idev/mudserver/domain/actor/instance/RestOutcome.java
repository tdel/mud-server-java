package fr.idev.mudserver.domain.actor.instance;

import java.util.Map;

public sealed interface RestOutcome {

    record Rested(Map<CharacterInstance, Integer> healedAmounts) implements RestOutcome {
    }

    record InCombat() implements RestOutcome {
    }

    record NoShortRestLeft() implements RestOutcome {
    }

    record NotEnoughProvisions(int totalValue) implements RestOutcome {
    }
}

package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

public record CharacterGainedXp(GamePlayer character, int amount) {
}

package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

public record CharacterReceivedGold(GamePlayer character, int amount) {
}

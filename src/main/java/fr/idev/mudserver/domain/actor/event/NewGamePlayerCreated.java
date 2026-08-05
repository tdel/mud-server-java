package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

public record NewGamePlayerCreated(GamePlayer character) {
}

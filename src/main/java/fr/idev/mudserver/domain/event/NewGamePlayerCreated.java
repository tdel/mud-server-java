package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.GamePlayer;

public record NewGamePlayerCreated(GamePlayer character) {
}

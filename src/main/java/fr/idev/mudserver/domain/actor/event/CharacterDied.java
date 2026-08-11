package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;

public record CharacterDied(GameMonster character, GamePlayer killer) {
}

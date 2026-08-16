package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterDied(MonsterInstance character, CharacterInstance killer) {
}

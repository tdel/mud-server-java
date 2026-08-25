package app.domain.actor.event;

import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.CharacterInstance;

public record CharacterDied(MonsterInstance character, CharacterInstance killer) {
}

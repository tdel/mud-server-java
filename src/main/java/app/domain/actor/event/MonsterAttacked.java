package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.MonsterInstance;

public record MonsterAttacked(MonsterInstance monster, CharacterInstance attacker) {
}

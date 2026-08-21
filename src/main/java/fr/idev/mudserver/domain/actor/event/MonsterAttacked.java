package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;

public record MonsterAttacked(MonsterInstance monster, CharacterInstance attacker) {
}

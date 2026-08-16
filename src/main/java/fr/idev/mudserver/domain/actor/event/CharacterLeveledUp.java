package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterLeveledUp(CharacterInstance character, int newLevel, int hpGained) {
}

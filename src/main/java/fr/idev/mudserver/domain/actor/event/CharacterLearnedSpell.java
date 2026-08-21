package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterLearnedSpell(CharacterInstance character, Spell spell) {
}

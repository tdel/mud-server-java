package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;

public record AppearanceComponent(Race race, Gender gender, CharacterClass characterClass) {
}

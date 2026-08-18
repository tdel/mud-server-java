package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;

public class AppearanceComponent {

    public Race race;
    public Gender gender;
    public CharacterClass characterClass;

    public AppearanceComponent(Race race, Gender gender, CharacterClass characterClass) {
        this.race = race;
        this.gender = gender;
        this.characterClass = characterClass;
    }
}

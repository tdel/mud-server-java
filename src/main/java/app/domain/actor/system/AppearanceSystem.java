package app.domain.actor.system;

import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.instance.CharacterInstance;

public final class AppearanceSystem {

    private final CharacterInstance character;
    private final Gender gender;
    private final Race race;

    public AppearanceSystem(CharacterInstance character, Gender gender, Race race) {
        this.character = character;
        this.gender = gender;
        this.race = race;
    }

    public Gender getGender() {
        return gender;
    }

    public Race getRace() {
        return race;
    }
}

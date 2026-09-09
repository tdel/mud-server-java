package app.domain.actor.system;

import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.instance.PlayerInstance;

public final class AppearanceSystem {

    private final PlayerInstance character;
    private final Gender gender;
    private final Race race;

    public AppearanceSystem(PlayerInstance character, Gender gender, Race race) {
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

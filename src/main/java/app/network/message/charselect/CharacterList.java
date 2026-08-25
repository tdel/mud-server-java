package app.network.message.charselect;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.actor.CharacterClass;
import app.domain.actor.Race;

public record CharacterList(List<Entry> characters) implements OutputJsonMessage {

    public record Entry(String name, Race race, CharacterClass characterClass, int level) {
    }

}

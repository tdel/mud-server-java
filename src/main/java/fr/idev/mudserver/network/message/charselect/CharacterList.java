package fr.idev.mudserver.network.message.charselect;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Race;

public record CharacterList(List<Entry> characters) implements OutputJsonMessage {

    public record Entry(String name, Race race, CharacterClass characterClass, int level) {
    }

}

package app.network.message.charselect;

import java.util.Map;

import app.network.OutputJsonMessage;
import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;

public record ChooseClass(Map<CharacterClass, Integer> hitDiceByClass,
        Map<CharacterClass, Attribute> primaryAbilityByClass) implements OutputJsonMessage {

}

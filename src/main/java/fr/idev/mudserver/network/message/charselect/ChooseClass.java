package fr.idev.mudserver.network.message.charselect;

import java.util.Map;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;

public record ChooseClass(Map<CharacterClass, Integer> hitDiceByClass,
        Map<CharacterClass, Attribute> primaryAbilityByClass) implements OutputJsonMessage {

}

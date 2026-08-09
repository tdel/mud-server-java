package fr.idev.mudserver.network.message.authed;

import java.util.Map;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ChooseClass(Map<CharacterClass, Integer> hitDiceByClass,
        Map<CharacterClass, Attribute> primaryAbilityByClass) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder("Choose your character's class:");
        for (CharacterClass characterClass : CharacterClass.values()) {
            text.append("\n  ").append(characterClass.label()).append(" - d").append(hitDiceByClass.get(characterClass))
                    .append(" hit die, primary ability ").append(primaryAbilityByClass.get(characterClass).label());
        }
        text.append("\n");
        output.write(text.toString());
    }
}

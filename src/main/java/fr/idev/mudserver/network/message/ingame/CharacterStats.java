package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record CharacterStats(Character character) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        Character c = character;
        output.write(String.format(
                "== %s ==\nHealth: %d/%d  Mana: %d/%d\nStrength: %d  Dexterity: %d  Constitution: %d\nIntelligence: %d  Wisdom: %d  Charisma: %d\n",
                c.getName(), c.getCurrentHealth(), c.getMaxHealth(), c.getCurrentMana(), c.getMaxMana(),
                c.getStrength(), c.getDexterity(), c.getConstitution(), c.getIntelligence(), c.getWisdom(),
                c.getCharisma()));
    }
}

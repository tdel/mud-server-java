package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record MonsterStatBlock(MonsterInstance monster, int armorClass, int strength, int strengthModifier,
        int dexterity, int dexterityModifier, int constitution, int constitutionModifier, int intelligence,
        int intelligenceModifier, int wisdom, int wisdomModifier, int charisma,
        int charismaModifier) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        MonsterInstance m = monster;
        CombatComponent combat = m.component(CombatComponent.class);
        output.write(String.format(
                "== %s ==\nHealth: %d/%d\nArmor Class: %d\nStrength: %d (%+d)  Dexterity: %d (%+d)  Constitution: %d (%+d)\nIntelligence: %d (%+d)  Wisdom: %d (%+d)  Charisma: %d (%+d)\n",
                Ansi.monster(m.getName()), combat.currentHealth(), combat.maxHealth(), armorClass, strength,
                strengthModifier, dexterity, dexterityModifier, constitution, constitutionModifier, intelligence,
                intelligenceModifier, wisdom, wisdomModifier, charisma, charismaModifier));
    }
}

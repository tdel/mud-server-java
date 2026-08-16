package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import java.util.Optional;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;
import fr.idev.mudserver.network.message.ingame.MonsterStatBlock;
import fr.idev.mudserver.network.message.ingame.NpcDescription;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Examine implements ControllerHandler {

    private final InventorySystem inventorySystem;
    private final LevelingSystem levelingSystem;
    private final AttributeSystem attributeSystem;

    public Examine(InventorySystem inventorySystem, LevelingSystem levelingSystem, AttributeSystem attributeSystem) {
        this.inventorySystem = inventorySystem;
        this.levelingSystem = levelingSystem;
        this.attributeSystem = attributeSystem;
    }

    @Override
    public String name() {
        return "examine";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("examine <name>"));
            return;
        }

        Optional<AbstractCharacter> target = character.getCurrentRoom().findOccupantByName(name);

        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        switch (target.get()) {
            case CharacterInstance p -> connection.send(new GamePlayerStats(p, inventorySystem.getArmorClass(p),
                    levelingSystem.getProficiencyBonus(p), attributeSystem.getAttribute(p, Attribute.STRENGTH),
                    attributeSystem.getModifier(p, Attribute.STRENGTH),
                    attributeSystem.getAttribute(p, Attribute.DEXTERITY),
                    attributeSystem.getModifier(p, Attribute.DEXTERITY),
                    attributeSystem.getAttribute(p, Attribute.CONSTITUTION),
                    attributeSystem.getModifier(p, Attribute.CONSTITUTION),
                    attributeSystem.getAttribute(p, Attribute.INTELLIGENCE),
                    attributeSystem.getModifier(p, Attribute.INTELLIGENCE),
                    attributeSystem.getAttribute(p, Attribute.WISDOM), attributeSystem.getModifier(p, Attribute.WISDOM),
                    attributeSystem.getAttribute(p, Attribute.CHARISMA),
                    attributeSystem.getModifier(p, Attribute.CHARISMA)));
            case MonsterInstance m -> connection.send(new MonsterStatBlock(m, inventorySystem.getArmorClass(m),
                    attributeSystem.getAttribute(m, Attribute.STRENGTH),
                    attributeSystem.getModifier(m, Attribute.STRENGTH),
                    attributeSystem.getAttribute(m, Attribute.DEXTERITY),
                    attributeSystem.getModifier(m, Attribute.DEXTERITY),
                    attributeSystem.getAttribute(m, Attribute.CONSTITUTION),
                    attributeSystem.getModifier(m, Attribute.CONSTITUTION),
                    attributeSystem.getAttribute(m, Attribute.INTELLIGENCE),
                    attributeSystem.getModifier(m, Attribute.INTELLIGENCE),
                    attributeSystem.getAttribute(m, Attribute.WISDOM), attributeSystem.getModifier(m, Attribute.WISDOM),
                    attributeSystem.getAttribute(m, Attribute.CHARISMA),
                    attributeSystem.getModifier(m, Attribute.CHARISMA)));
            case AbstractNpc n -> connection.send(new NpcDescription(n));
            default -> throw new IllegalStateException("Type de cible inattendu : " + target.get().getClass());
        }
    }
}

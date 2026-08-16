package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;

@Component
public class Stats implements ControllerHandler {

    private final InventorySystem inventorySystem;
    private final LevelingSystem levelingSystem;
    private final AttributeSystem attributeSystem;

    public Stats(InventorySystem inventorySystem, LevelingSystem levelingSystem, AttributeSystem attributeSystem) {
        this.inventorySystem = inventorySystem;
        this.levelingSystem = levelingSystem;
        this.attributeSystem = attributeSystem;
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        connection.send(new GamePlayerStats(character, inventorySystem.getArmorClass(character),
                levelingSystem.getProficiencyBonus(character),
                attributeSystem.getAttribute(character, Attribute.STRENGTH),
                attributeSystem.getModifier(character, Attribute.STRENGTH),
                attributeSystem.getAttribute(character, Attribute.DEXTERITY),
                attributeSystem.getModifier(character, Attribute.DEXTERITY),
                attributeSystem.getAttribute(character, Attribute.CONSTITUTION),
                attributeSystem.getModifier(character, Attribute.CONSTITUTION),
                attributeSystem.getAttribute(character, Attribute.INTELLIGENCE),
                attributeSystem.getModifier(character, Attribute.INTELLIGENCE),
                attributeSystem.getAttribute(character, Attribute.WISDOM),
                attributeSystem.getModifier(character, Attribute.WISDOM),
                attributeSystem.getAttribute(character, Attribute.CHARISMA),
                attributeSystem.getModifier(character, Attribute.CHARISMA)));
    }
}

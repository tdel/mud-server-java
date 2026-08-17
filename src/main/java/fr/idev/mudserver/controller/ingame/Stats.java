package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;

@Component
public class Stats implements ControllerHandler {

    private final InventorySystem inventorySystem;
    private final LevelingSystem levelingSystem;

    public Stats(InventorySystem inventorySystem, LevelingSystem levelingSystem) {
        this.inventorySystem = inventorySystem;
        this.levelingSystem = levelingSystem;
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
        AttributeComponent attributes = character.component(AttributeComponent.class);
        connection.send(new GamePlayerStats(character, inventorySystem.getArmorClass(character),
                character.component(LevelingComponent.class).proficiencyBonus(), attributes.valueOf(Attribute.STRENGTH),
                attributes.modifier(Attribute.STRENGTH), attributes.valueOf(Attribute.DEXTERITY),
                attributes.modifier(Attribute.DEXTERITY), attributes.valueOf(Attribute.CONSTITUTION),
                attributes.modifier(Attribute.CONSTITUTION), attributes.valueOf(Attribute.INTELLIGENCE),
                attributes.modifier(Attribute.INTELLIGENCE), attributes.valueOf(Attribute.WISDOM),
                attributes.modifier(Attribute.WISDOM), attributes.valueOf(Attribute.CHARISMA),
                attributes.modifier(Attribute.CHARISMA)));
    }
}

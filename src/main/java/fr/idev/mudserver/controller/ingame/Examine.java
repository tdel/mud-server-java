package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import org.springframework.stereotype.Component;

import java.util.Optional;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
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

    public Examine(InventorySystem inventorySystem, LevelingSystem levelingSystem) {
        this.inventorySystem = inventorySystem;
        this.levelingSystem = levelingSystem;
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
            case CharacterInstance p -> {
                AttributeComponent attributes = p.component(AttributeComponent.class);
                connection.send(new GamePlayerStats(p, inventorySystem.getArmorClass(p),
                        p.component(LevelingComponent.class).proficiencyBonus(), attributes.valueOf(Attribute.STRENGTH),
                        attributes.modifier(Attribute.STRENGTH), attributes.valueOf(Attribute.DEXTERITY),
                        attributes.modifier(Attribute.DEXTERITY), attributes.valueOf(Attribute.CONSTITUTION),
                        attributes.modifier(Attribute.CONSTITUTION), attributes.valueOf(Attribute.INTELLIGENCE),
                        attributes.modifier(Attribute.INTELLIGENCE), attributes.valueOf(Attribute.WISDOM),
                        attributes.modifier(Attribute.WISDOM), attributes.valueOf(Attribute.CHARISMA),
                        attributes.modifier(Attribute.CHARISMA)));
            }
            case MonsterInstance m -> {
                AttributeComponent attributes = m.component(AttributeComponent.class);
                connection.send(new MonsterStatBlock(m, inventorySystem.getArmorClass(m),
                        attributes.valueOf(Attribute.STRENGTH), attributes.modifier(Attribute.STRENGTH),
                        attributes.valueOf(Attribute.DEXTERITY), attributes.modifier(Attribute.DEXTERITY),
                        attributes.valueOf(Attribute.CONSTITUTION), attributes.modifier(Attribute.CONSTITUTION),
                        attributes.valueOf(Attribute.INTELLIGENCE), attributes.modifier(Attribute.INTELLIGENCE),
                        attributes.valueOf(Attribute.WISDOM), attributes.modifier(Attribute.WISDOM),
                        attributes.valueOf(Attribute.CHARISMA), attributes.modifier(Attribute.CHARISMA)));
            }
            case AbstractNpc n -> connection.send(new NpcDescription(n));
            default -> throw new IllegalStateException("Type de cible inattendu : " + target.get().getClass());
        }
    }
}

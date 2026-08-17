package fr.idev.mudserver.controller.ingame;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.util.Optional;
import java.util.Set;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.system.CombatSystem;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Attack implements ControllerHandler {

    private final CombatEngine combatEngine;
    private final CombatSystem combatSystem;

    public Attack(CombatEngine combatEngine, CombatSystem combatSystem) {
        this.combatEngine = combatEngine;
        this.combatSystem = combatSystem;
    }

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        AbstractCharacter target;
        if (name.isEmpty()) {
            target = character.component(CombatComponent.class).target();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }

            if (!(target instanceof MonsterInstance)) {
                return;
            }

            if (!character.component(PositionComponent.class).currentRoom().getMonsters().contains(target)) {
                combatSystem.setTarget(null, character);
                connection.send(new TargetNotFound(target.component(IdentityComponent.class).name()));
                return;
            }
        } else {
            Optional<MonsterInstance> found = character.component(PositionComponent.class).currentRoom()
                    .findMonsterByName(name);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            combatSystem.setTarget(target, character);
        }

        combatEngine.attack(character, (MonsterInstance) target);
    }
}

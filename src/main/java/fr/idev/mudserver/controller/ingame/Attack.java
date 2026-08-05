package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.CombatService;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.AttackResult;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

/**
 * Accepte deux formes : {@code attack} seul réutilise la cible déjà choisie via
 * {@code select <monster name>} ({@link GamePlayer#getTarget()}) ; {@code
 * attack <character name>} force une sélection au moment même de l'attaque, en
 * résolvant le nom dans la room courante — même logique que {@link Select}.
 */
@Component
public class Attack implements ControllerHandler {

    private final GameWorld gameWorld;
    private final CombatService combatService;

    public Attack(GameWorld gameWorld, CombatService combatService) {
        this.gameWorld = gameWorld;
        this.combatService = combatService;
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
        GamePlayer character = gameWorld.character(connection);
        String name = argument.trim();

        GameMonster target;
        if (name.isEmpty()) {
            target = character.getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
            if (!character.getCurrentRoom().getMonsters().contains(target)) {
                character.setTarget(null);
                connection.send(new TargetNotFound(target.getName()));
                return;
            }
        } else {
            Optional<GameMonster> found = character.getCurrentRoom().findMonsterByName(name);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            character.setTarget(target);
        }

        CombatResult result = combatService.tryAttack(character, target);
        connection.send(new AttackResult(result));

        if (result.hit()) {
            target.takeDamage(result.damage(), character);
        }
    }
}

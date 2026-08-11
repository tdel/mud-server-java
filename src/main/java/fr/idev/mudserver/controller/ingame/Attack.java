package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

/**
 * Accepte deux formes : {@code attack} seul réutilise la cible déjà choisie via
 * {@code select <monster name>} ({@link GamePlayer#getTarget()}) ; {@code
 * attack <character name>} force une sélection au moment même de l'attaque, en
 * résolvant le nom dans la room courante — même logique que {@link Select}. Le
 * champ {@code target} reste une simple commodité UX (« qui vise `attack` sans
 * argument »), indépendante de l'affrontement effectif
 * ({@link GamePlayer#getEncounter()}) — toute la logique de
 * rejoindre/fusionner/résoudre le tour est déléguée à {@link CombatEngine}.
 */
@Component
public class Attack implements ControllerHandler {

    private final CombatEngine combatEngine;

    public Attack(CombatEngine combatEngine) {
        this.combatEngine = combatEngine;
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
        GamePlayer character = connection.character();
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

        combatEngine.attack(character, target);
    }
}

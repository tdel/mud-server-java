package fr.idev.mudserver.domain.actor.system;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.component.BehaviorComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;

@Service
public class AiSystem {

    public Optional<UUID> currentTargetId(MonsterInstance monster) {
        return Optional.ofNullable(component(monster).currentTargetId());
    }

    public CharacterInstance chooseTarget(MonsterInstance monster, List<CharacterInstance> livingPlayers) {
        CharacterInstance target = livingPlayers.size() == 1
                ? livingPlayers.get(0)
                : livingPlayers.get(DiceRoller.roll(new DiceExpression(1, livingPlayers.size(), 0)).total() - 1);

        monster.updateComponent(BehaviorComponent.class, current -> new BehaviorComponent(target.getId()));
        return target;
    }

    public void clearTarget(MonsterInstance monster) {
        monster.updateComponent(BehaviorComponent.class, current -> BehaviorComponent.idle());
    }

    private BehaviorComponent component(MonsterInstance monster) {
        return monster.component(BehaviorComponent.class);
    }
}

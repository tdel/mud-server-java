package fr.idev.mudserver.domain.actor.system;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.component.BehaviorComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;

public final class AiSystem {

    private AiSystem() {
    }

    public static void attach(MonsterInstance monster) {
        monster.attachComponent(BehaviorComponent.idle());
    }

    public static Optional<UUID> currentTargetId(MonsterInstance monster) {
        return Optional.ofNullable(component(monster).currentTargetId());
    }

    public static CharacterInstance chooseTarget(MonsterInstance monster, List<CharacterInstance> livingPlayers) {
        CharacterInstance target = livingPlayers.size() == 1
                ? livingPlayers.get(0)
                : livingPlayers.get(DiceRoller.roll(new DiceExpression(1, livingPlayers.size(), 0)).total() - 1);

        monster.updateComponent(BehaviorComponent.class, current -> new BehaviorComponent(target.getId()));
        return target;
    }

    public static void clearTarget(MonsterInstance monster) {
        monster.updateComponent(BehaviorComponent.class, current -> BehaviorComponent.idle());
    }

    private static BehaviorComponent component(MonsterInstance monster) {
        return monster.component(BehaviorComponent.class);
    }
}

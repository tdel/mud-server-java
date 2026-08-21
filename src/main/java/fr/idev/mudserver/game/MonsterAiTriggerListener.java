package fr.idev.mudserver.game;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.MonsterAttacked;

@Component
public class MonsterAiTriggerListener {

    private final MonsterAiEngine monsterAiEngine;

    public MonsterAiTriggerListener(MonsterAiEngine monsterAiEngine) {
        this.monsterAiEngine = monsterAiEngine;
    }

    @EventListener
    void onMonsterAttacked(MonsterAttacked event) {
        monsterAiEngine.aggro(event.monster(), event.attacker());
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        monsterAiEngine.forget(event.character());
    }
}

package fr.idev.mudserver.game;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.GamePlayerDamaged;
import fr.idev.mudserver.domain.actor.event.SpellCast;

@Component
public class RegenTriggerListener {

    private final RegenHealthEngine regenHealthEngine;
    private final RegenManaEngine regenManaEngine;

    public RegenTriggerListener(RegenHealthEngine regenHealthEngine, RegenManaEngine regenManaEngine) {
        this.regenHealthEngine = regenHealthEngine;
        this.regenManaEngine = regenManaEngine;
    }

    @EventListener
    void onGamePlayerDamaged(GamePlayerDamaged event) {
        regenHealthEngine.register(event.character());
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        regenManaEngine.register(event.caster());
    }
}

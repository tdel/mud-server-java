package fr.idev.mudserver.game;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.GamePlayerDamaged;
import fr.idev.mudserver.domain.actor.event.SpellCast;

@Component
public class RegenTriggerListener {

    private final RegenEngine regenEngine;

    public RegenTriggerListener(RegenEngine regenEngine) {
        this.regenEngine = regenEngine;
    }

    @EventListener
    void onGamePlayerDamaged(GamePlayerDamaged event) {
        regenEngine.register(event.character());
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        regenEngine.register(event.caster());
    }
}

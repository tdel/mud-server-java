package fr.idev.mudserver.game;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.event.CharacterEffectExpired;
import fr.idev.mudserver.domain.actor.event.SpellCast;
import fr.idev.mudserver.network.message.ingame.SpellModifierExpired;

@Component
public class BuffTriggerListener {

    private final BuffExpiryEngine buffExpiryEngine;

    public BuffTriggerListener(BuffExpiryEngine buffExpiryEngine) {
        this.buffExpiryEngine = buffExpiryEngine;
    }

    @EventListener
    void onSpellCast(SpellCast event) {
        boolean modifier = event.spell().effect() == SpellEffectType.BUFF
                || event.spell().effect() == SpellEffectType.DEBUFF;
        if (event.hit() && modifier) {
            buffExpiryEngine.register(event.target());
        }
    }

    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        event.character().getCurrentRoom()
                .broadcast(new SpellModifierExpired(event.character().getName(), event.effect().spellName()), null);
    }
}

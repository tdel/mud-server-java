package app.network.message.ingame;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import app.domain.ActiveEffect;
import app.domain.EffectCategory;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.system.InventorySystem;

/**
 * Buff/debuff actif d'un membre du groupe, tel qu'affiché dans la fenêtre de
 * groupe des autres membres (icône + infobulle). Joint à PartyJoined.MemberView
 * et PartyMemberJoined pour les effets déjà actifs à l'entrée dans le groupe —
 * les suivants arrivent par PartyMemberEffectApplied/PartyMemberEffectExpired.
 * La pénalité de grade d'équipement (InventorySystem) est exclue : elle n'est
 * ni un sort ni annoncée au groupe quand elle change.
 */
public record PartyEffectView(String skillName, boolean beneficial, long secondsRemaining) {

    public static List<PartyEffectView> listOf(AbstractCharacter character) {
        Instant now = Instant.now();
        return character.getEffectsSystem().active().stream()
                .filter(effect -> !effect.skillId().equals(InventorySystem.GRADE_PENALTY_EFFECT_ID))
                .map(effect -> of(effect, now)).toList();
    }

    private static PartyEffectView of(ActiveEffect effect, Instant now) {
        long secondsRemaining = Math.max(0, Duration.between(now, effect.expiresAt()).toSeconds());
        return new PartyEffectView(effect.skillName(), effect.category() == EffectCategory.BUFF, secondsRemaining);
    }
}

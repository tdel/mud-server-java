package app.network.message.ingame;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import app.domain.AbnormalStatus;
import app.domain.ActiveEffect;
import app.domain.CubicType;
import app.domain.EffectCategory;
import app.domain.PeriodicDamage;
import app.domain.StatModifier;
import app.domain.actor.AbstractCharacter;

/**
 * Buff/debuff actif d'un personnage (joueur, PNJ ou monstre), tel qu'affiché
 * par le client : icône + infobulle (modificateurs, poison, temps restant).
 * Joint à EntityView (effets déjà actifs quand l'entité entre en perception),
 * GamePlayerStats (nos propres effets), PartyJoined/PartyMemberJoined, et porté
 * par EffectApplied — les fins arrivent par SkillModifierExpired.
 *
 * {@code beneficial} : même règle que ActiveEffect.category().
 * {@code permanent} : effet sans échéance (pénalité de grade d'équipement, cf.
 * GradePenalty ; toggles), secondsRemaining vaut alors 0. {@code status} :
 * altération d'état (STUN, SLEEP, ROOT...), null sinon ; {@code toggle} :
 * toggle actif (Vicious Stance) ; {@code cubic} : cubique porté (STORM...).
 */
public record ActiveEffectView(String skillName, boolean beneficial, long secondsRemaining,
        List<StatModifier> modifiers, int periodicDamage, long periodicIntervalSeconds, boolean breaksOnAction,
        boolean permanent, AbnormalStatus status, boolean toggle, CubicType cubic) {

    public static List<ActiveEffectView> listOf(AbstractCharacter character) {
        Instant now = Instant.now();
        return character.getEffectsSystem().active().stream().map(effect -> of(effect, now)).toList();
    }

    // Arrondi à la seconde la plus proche : un effet de 30 s tout juste posé
    // s'annonce 30 s, pas 29.
    public static ActiveEffectView of(ActiveEffect effect, Instant now) {
        long secondsRemaining = effect.isPermanent()
                ? 0
                : Math.max(0, Math.round(Duration.between(now, effect.expiresAt()).toMillis() / 1000.0));
        PeriodicDamage periodic = effect.periodicDamage();
        return new ActiveEffectView(effect.skillName(), effect.category() == EffectCategory.BUFF, secondsRemaining,
                effect.modifiers(), periodic == null ? 0 : periodic.amount(),
                periodic == null ? 0 : periodic.intervalMs() / 1000, effect.breaksOnAction(), effect.isPermanent(),
                effect.status(), effect.isToggle(), effect.cubic());
    }
}

package app.domain;

import java.time.Instant;
import java.util.UUID;

import app.domain.actor.ModifiedStat;

public record ActiveEffect(UUID skillId, String skillName, ModifiedStat stat, int amount, Instant expiresAt) {

    public EffectCategory category() {
        return amount >= 0 ? EffectCategory.BUFF : EffectCategory.DEBUFF;
    }
}

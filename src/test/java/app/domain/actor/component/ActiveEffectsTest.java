package app.domain.actor.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import app.domain.actor.ModifiedStat;

class ActiveEffectsTest {

    @Test
    void categoryIsDerivedFromAmountSign() {
        ActiveEffect buff = effect(5, Instant.now().plusSeconds(60));
        ActiveEffect debuff = effect(-5, Instant.now().plusSeconds(60));

        assertThat(buff.category()).isEqualTo(EffectCategory.BUFF);
        assertThat(debuff.category()).isEqualTo(EffectCategory.DEBUFF);
    }

    @Test
    void applyBeyondMaxBuffSlotsEvictsSoonestToExpire() {
        ActiveEffects effects = new ActiveEffects();
        ActiveEffect soonest = effect(1, Instant.now().plusSeconds(10));
        effects.apply(soonest);
        for (int i = 1; i < ActiveEffects.MAX_BUFF_SLOTS; i++) {
            effects.apply(effect(1, Instant.now().plusSeconds(100 + i)));
        }
        assertThat(effects.active()).hasSize(ActiveEffects.MAX_BUFF_SLOTS);

        Optional<ActiveEffect> evicted = effects.apply(effect(1, Instant.now().plusSeconds(200)));

        assertThat(evicted).contains(soonest);
        assertThat(effects.active()).hasSize(ActiveEffects.MAX_BUFF_SLOTS);
    }

    @Test
    void applyBeyondMaxDebuffSlotsEvictsSoonestToExpire() {
        ActiveEffects effects = new ActiveEffects();
        ActiveEffect soonest = effect(-1, Instant.now().plusSeconds(10));
        effects.apply(soonest);
        for (int i = 1; i < ActiveEffects.MAX_DEBUFF_SLOTS; i++) {
            effects.apply(effect(-1, Instant.now().plusSeconds(100 + i)));
        }

        Optional<ActiveEffect> evicted = effects.apply(effect(-1, Instant.now().plusSeconds(200)));

        assertThat(evicted).contains(soonest);
        assertThat(effects.active()).hasSize(ActiveEffects.MAX_DEBUFF_SLOTS);
    }

    @Test
    void refreshingSameSpellIdNeverEvicts() {
        ActiveEffects effects = new ActiveEffects();
        UUID spellId = UUID.randomUUID();
        effects.apply(new ActiveEffect(spellId, "spell", ModifiedStat.PATK, 1, Instant.now().plusSeconds(1)));
        for (int i = 1; i < ActiveEffects.MAX_BUFF_SLOTS; i++) {
            effects.apply(effect(1, Instant.now().plusSeconds(100 + i)));
        }
        assertThat(effects.active()).hasSize(ActiveEffects.MAX_BUFF_SLOTS);

        ActiveEffect refreshed = new ActiveEffect(spellId, "spell", ModifiedStat.PATK, 1,
                Instant.now().plusSeconds(500));
        Optional<ActiveEffect> evicted = effects.apply(refreshed);

        assertThat(evicted).isEmpty();
        assertThat(effects.active()).hasSize(ActiveEffects.MAX_BUFF_SLOTS);
    }

    private ActiveEffect effect(int amount, Instant expiresAt) {
        return new ActiveEffect(UUID.randomUUID(), "spell", ModifiedStat.PATK, amount, expiresAt);
    }
}

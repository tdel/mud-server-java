package app.domain;

import app.domain.actor.AbstractCharacter;

// Dégâts infligés à intervalle fixe tant que l'effet porteur est actif
// (Curse: Poison, Bleed). `source` est crédité du coup fatal (XP/loot) si la
// cible en meurt ; purement en mémoire, jamais persisté (cf.
// ActiveEffectPersistenceListener).
public record PeriodicDamage(int amount, long intervalMs, AbstractCharacter source, PeriodicKind kind) {

    public PeriodicDamage(int amount, long intervalMs, AbstractCharacter source) {
        this(amount, intervalMs, source, PeriodicKind.POISON);
    }
}

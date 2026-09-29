package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Période de dégâts d'un effet actif (Curse: Poison), diffusée à portée de la
// cible (celle-ci comprise). sourceId : lanceur du poison, crédité du coup fatal.
public record EffectDamage(UUID targetId, String targetName, String skillName, int amount, int targetHealthAfter,
        int targetMaxHealth, boolean targetDefeated, UUID sourceId) implements OutputJsonMessage {
}

package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Buff/debuff posé (ou renouvelé) sur un personnage, diffusé à sa KnownList
 * (lui compris) : état à afficher sous ses jauges, distinct de
 * SkillModifierAnnounced qui annonce le lancer du sort (effets visuels,
 * journal, mana du lanceur). La fin arrive par SkillModifierExpired.
 */
public record EffectApplied(UUID characterId, String characterName,
        ActiveEffectView effect) implements OutputJsonMessage {

}

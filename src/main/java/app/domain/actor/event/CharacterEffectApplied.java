package app.domain.actor.event;

import app.domain.ActiveEffect;
import app.domain.actor.AbstractCharacter;

// Un buff/debuff vient d'être posé (ou renouvelé) sur `character` par une
// compétence — effet principal d'un sort BUFF/DEBUFF (SkillSystem.castModifier)
// ou effet secondaire d'un sort de dégâts (SkillEffectApplier). Écouté pour le
// suivi d'expiration et la diffusion aux clients (ActiveEffectEngine), la
// fenêtre de groupe (PartyEngine) et la persistance.
public record CharacterEffectApplied(AbstractCharacter character, ActiveEffect effect) {
}

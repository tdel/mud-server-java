package app.domain.actor.event;

import app.domain.PassiveSkill;
import app.domain.actor.instance.CharacterInstance;

// Publié par SkillLearningEngine une fois que SkillSystem.learn(PassiveSkill, int)
// a déjà appliqué le nouveau level en mémoire — jamais avant. previousLevel==0
// signifie une première acquisition (ex: Expertise Grade niveau 20), sinon une
// amélioration d'un level déjà connu (ex: niveau 40).
public record CharacterLearnedPassiveSkill(CharacterInstance character, PassiveSkill passiveSkill, int newLevel,
        int previousLevel) {
}

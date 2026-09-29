package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.game.SkillTrainer;
import app.network.OutputJsonMessage;

// Réponse à skill-list / learn-skill : compétences apprenables maintenant
// auprès de ce PNJ SKILL_LEARNER (un level à la fois : `level` = level
// proposé, `currentLevel` = level connu, 0 si nouvelle). nextLevel : level de
// personnage du prochain palier, null si l'arbre de la classe est épuisé.
// skillType : type du sort actif, ou "PASSIVE".
public record LearnableSkills(UUID npcId, String npcName, int characterLevel, Integer nextLevel,
        List<Entry> skills) implements OutputJsonMessage {

    public record Entry(UUID id, String name, String description, String skillType, int level, int currentLevel,
            int maxLevel, int requiredLevel, int manaCost, int castTimeMs, int cooldownSeconds, int range) {

        public static Entry of(SkillTrainer.Offer offer) {
            if (offer.passive()) {
                return new Entry(offer.skillId(), offer.name(), offer.description(), "PASSIVE", offer.level(),
                        offer.currentLevel(), offer.maxLevel(), offer.requiredLevel(), 0, 0, 0, 0);
            }
            var skill = offer.activeSkill();
            return new Entry(offer.skillId(), offer.name(), offer.description(), skill.skillType().name(),
                    offer.level(), offer.currentLevel(), offer.maxLevel(), offer.requiredLevel(),
                    skill.manaCostAt(offer.level()), skill.castingTimeMs(), skill.reuseTimeMs() / 1000, skill.range());
        }
    }
}

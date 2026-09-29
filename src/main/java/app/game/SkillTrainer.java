package app.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.actor.CharacterClass.SkillTreeEntry;
import app.domain.actor.event.CharacterLearnedPassiveSkill;
import app.domain.actor.event.CharacterLearnedSkill;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.system.SkillSystem.LearnResult;
import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.SkillCatalog;

// Apprentissage auprès d'un PNJ SKILL_LEARNER (Grand Master de L2) : pour
// chaque compétence de l'arbre de la classe, seul le level suivant celui déjà
// connu est proposé, dès que le level de personnage requis est atteint. Les
// levels autoGet (compétences de départ, Expertise Grade) restent appris
// d'office par SkillLearningEngine et ne passent jamais par ici.
@Service
public class SkillTrainer {

    private static final Logger log = LoggerFactory.getLogger(SkillTrainer.class);

    private final SkillCatalog skillCatalog;
    private final PassiveSkillCatalog passiveSkillCatalog;

    public SkillTrainer(SkillCatalog skillCatalog, PassiveSkillCatalog passiveSkillCatalog) {
        this.skillCatalog = skillCatalog;
        this.passiveSkillCatalog = passiveSkillCatalog;
    }

    // `available` : apprenable maintenant ; sinon le level de personnage n'est
    // pas encore atteint (sert à annoncer le prochain palier).
    public record Offer(UUID skillId, String name, String description, boolean passive, ActiveSkill activeSkill,
            int level, int currentLevel, int maxLevel, int requiredLevel, boolean available) {
    }

    public List<Offer> offers(PlayerInstance character) {
        List<SkillTreeEntry> tree = character.getClassSystem().getCharacterClass().tree();
        Set<UUID> skillIds = new LinkedHashSet<>();
        tree.forEach(entry -> skillIds.add(entry.skillId()));

        int characterLevel = character.getLevelingSystem().getLevel();
        List<Offer> offers = new ArrayList<>();
        for (UUID skillId : skillIds) {
            int current = knownLevel(character, skillId);
            Optional<SkillTreeEntry> next = entryFor(tree, skillId, current + 1);
            if (next.isEmpty() || next.get().autoGet()) {
                continue;
            }
            int maxLevel = (int) tree.stream().filter(entry -> entry.skillId().equals(skillId)).count();
            SkillTreeEntry entry = next.get();
            if (skillCatalog.isKnownId(skillId)) {
                ActiveSkill skill = skillCatalog.getById(skillId);
                offers.add(new Offer(skillId, skill.name(), skill.description(), false, skill, entry.level(), current,
                        maxLevel, entry.playerLevel(), entry.playerLevel() <= characterLevel));
            } else if (passiveSkillCatalog.isKnownId(skillId)) {
                PassiveSkill skill = passiveSkillCatalog.getById(skillId);
                offers.add(new Offer(skillId, skill.name(), skill.description(), true, null, entry.level(), current,
                        maxLevel, entry.playerLevel(), entry.playerLevel() <= characterLevel));
            }
        }
        return offers;
    }

    // Plus petit level de personnage débloquant une nouvelle compétence, vide si
    // l'arbre est épuisé.
    public OptionalInt nextLevel(List<Offer> offers) {
        return offers.stream().filter(offer -> !offer.available()).mapToInt(Offer::requiredLevel).min();
    }

    public sealed interface LearnOutcome {

        record Learned(String skillName, int level) implements LearnOutcome {
        }

        record Refused(String skillName, String reason, int requiredLevel) implements LearnOutcome {
        }
    }

    public LearnOutcome learn(PlayerInstance character, UUID skillId) {
        List<SkillTreeEntry> tree = character.getClassSystem().getCharacterClass().tree();
        boolean active = skillCatalog.isKnownId(skillId);
        boolean passive = passiveSkillCatalog.isKnownId(skillId);
        String name = active
                ? skillCatalog.getById(skillId).name()
                : passive ? passiveSkillCatalog.getById(skillId).name() : "";
        if (!active && !passive || tree.stream().noneMatch(entry -> entry.skillId().equals(skillId))) {
            return new LearnOutcome.Refused(name, "NOT_IN_TREE", 0);
        }

        int current = knownLevel(character, skillId);
        Optional<SkillTreeEntry> next = entryFor(tree, skillId, current + 1);
        if (next.isEmpty()) {
            return new LearnOutcome.Refused(name, "MAX_LEVEL", 0);
        }
        if (next.get().autoGet()) {
            return new LearnOutcome.Refused(name, "AUTO_LEARNED", next.get().playerLevel());
        }
        if (next.get().playerLevel() > character.getLevelingSystem().getLevel()) {
            return new LearnOutcome.Refused(name, "LEVEL_TOO_LOW", next.get().playerLevel());
        }

        int level = next.get().level();
        if (active) {
            ActiveSkill skill = skillCatalog.getById(skillId);
            LearnResult result = character.getSkillSystem().learn(skill, level);
            if (result != LearnResult.ALREADY_KNOWN) {
                DomainEventPublisher.publish(new CharacterLearnedSkill(character, skill, level, current));
            }
        } else {
            PassiveSkill skill = passiveSkillCatalog.getById(skillId);
            if (character.getSkillSystem().learn(skill, level)) {
                DomainEventPublisher.publish(new CharacterLearnedPassiveSkill(character, skill, level, current));
            }
        }
        log.info("character.skill_learned_from_trainer character={} skill={} level={} characterLevel={}",
                character.getName(), name, level, character.getLevelingSystem().getLevel());
        return new LearnOutcome.Learned(name, level);
    }

    private static int knownLevel(PlayerInstance character, UUID skillId) {
        return Math.max(character.getSkillSystem().levelOf(skillId),
                character.getSkillSystem().passiveLevelOf(skillId));
    }

    private static Optional<SkillTreeEntry> entryFor(List<SkillTreeEntry> tree, UUID skillId, int level) {
        return tree.stream().filter(entry -> entry.skillId().equals(skillId) && entry.level() == level).findFirst();
    }
}

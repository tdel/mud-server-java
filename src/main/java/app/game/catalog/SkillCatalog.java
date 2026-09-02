package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.ActiveSkill;
import app.domain.EffectCategory;
import app.domain.SkillEffectDefinition;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.SkillTargetType;
import app.domain.StatModifier;
import app.domain.actor.CharacterClass;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@Service
public class SkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);

    private static final String SKILL_RESOURCE = "/data/skills/skills.xml";

    private final Map<UUID, ActiveSkill> activeSkills = new ConcurrentHashMap<>();

    private final XmlMapper xmlMapper;

    public SkillCatalog(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public void warmSkills() {
        try (InputStream in = getClass().getResourceAsStream(SKILL_RESOURCE)) {
            List<SkillDefinition> definitions = xmlMapper.readValue(in, new TypeReference<List<SkillDefinition>>() {
            });
            for (SkillDefinition definition : definitions) {
                if (definition.projectile() && definition.projectileSpeed() <= 0) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") est un projectile mais n'a pas de projectileSpeed positif dans " + SKILL_RESOURCE);
                }
                if (definition.power().size() != definition.manaCost().size()) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") a des tableaux power/manaCost de longueurs différentes dans " + SKILL_RESOURCE);
                }
                if (definition.power().isEmpty()) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") n'a aucun level (power vide) dans " + SKILL_RESOURCE);
                }
                int aoeRadius = definition.aoeRadius() == null ? 0 : definition.aoeRadius();
                SkillTargetType target = definition.target() == null ? SkillTargetType.ONE : definition.target();
                if (target == SkillTargetType.AOE && aoeRadius <= 0) {
                    log.warn(
                            "activeSkill.aoe_radius_missing id={} name={} — utilisation de range={} comme rayon de zone",
                            definition.id(), definition.name(), definition.range());
                }
                List<SkillEffectDefinition> effects = definition.effects() == null
                        ? List.of()
                        : definition.effects().stream().map(
                                e -> new SkillEffectDefinition(e.name(), e.time(), e.power(), e.type(), e.effect()))
                                .toList();
                ActiveSkill activeSkill = new ActiveSkill(definition.id(), definition.name(), definition.manaCost(),
                        definition.cooldownSeconds(), definition.castingTimeMs(), definition.range(), aoeRadius,
                        definition.skillType(), target, definition.power(),
                        definition.element() == null ? SkillElement.NONE : definition.element(),
                        definition.projectile(), definition.projectileSpeed(), effects);
                if (activeSkills.containsKey(activeSkill.id())) {
                    throw new IllegalStateException("ActiveSkill " + activeSkill.id() + " (" + activeSkill.name()
                            + ") a un id déjà utilisé par " + activeSkills.get(activeSkill.id()).name() + " dans "
                            + SKILL_RESOURCE);
                }
                activeSkills.put(activeSkill.id(), activeSkill);
            }
            log.info("activeSkill.templates_loaded count={}", activeSkills.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + SKILL_RESOURCE, e);
        }
    }

    public ActiveSkill getById(UUID skillId) {
        ActiveSkill activeSkill = activeSkills.get(skillId);
        if (activeSkill == null) {
            throw new IllegalStateException(
                    "ActiveSkill " + skillId + " absent du cache — warmSkills() a-t-il été appelé ?");
        }
        return activeSkill;
    }

    public List<LearnableSkill> skillsLearnableAt(CharacterClass characterClass, int level) {
        return characterClass.learnableSkillIds(level).stream()
                .map(learnable -> new LearnableSkill(getById(learnable.skillId()), learnable.level())).toList();
    }

    public record LearnableSkill(ActiveSkill skill, int level) {
    }

    private record SkillDefinition(UUID id, String name,
            @JacksonXmlElementWrapper(useWrapping = false) List<Integer> manaCost, int cooldownSeconds,
            int castingTimeMs, int range, Integer aoeRadius, SkillEffectType skillType, SkillTargetType target,
            @JacksonXmlElementWrapper(useWrapping = false) List<Integer> power, SkillElement element,
            boolean projectile, int projectileSpeed,
            @JacksonXmlElementWrapper(useWrapping = false) List<SkillEffectDefinitionXml> effects) {
    }

    private record SkillEffectDefinitionXml(String name, int time, int power, EffectCategory type,
            @JacksonXmlElementWrapper(useWrapping = false) List<StatModifier> effect) {
    }
}

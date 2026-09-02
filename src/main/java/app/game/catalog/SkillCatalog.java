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
import app.domain.SkillLevel;
import app.domain.SkillTargetType;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.CharacterClass;
import app.domain.actor.ModifiedStat;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

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
            SkillsDocument document = xmlMapper.readValue(in, SkillsDocument.class);
            for (SkillDefinition definition : document.skills()) {
                if (definition.skillType() == SkillEffectType.PASSIVE) {
                    // Compétence passive (ex: Expertise Grade) : hors du champ de SkillCatalog,
                    // gérée symétriquement par PassiveSkillCatalog.
                    continue;
                }
                if (definition.levels() == null || definition.levels().isEmpty()) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") n'a aucun level dans " + SKILL_RESOURCE);
                }
                if (definition.reuseTime() == null || definition.castTime() == null || definition.range() == null) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") n'a pas reuseTime/castTime/range dans " + SKILL_RESOURCE);
                }
                List<SkillLevel> levels = definition.levels().stream().map(l -> {
                    if (l.mana() == null || l.power() == null) {
                        throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                                + ") a un level sans mana/power dans " + SKILL_RESOURCE);
                    }
                    return new SkillLevel(l.id(), l.mana(), l.power());
                }).toList();
                for (int i = 0; i < levels.size(); i++) {
                    if (levels.get(i).level() != i + 1) {
                        throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                                + ") a des level id non séquentiels dans " + SKILL_RESOURCE);
                    }
                }
                boolean projectile = definition.projectile() != null;
                int projectileSpeed = projectile ? definition.projectile().speed() : 0;
                if (projectile && projectileSpeed <= 0) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + ") est un projectile mais n'a pas de speed positif dans " + SKILL_RESOURCE);
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
                        : definition.effects().effect().stream()
                                .map(e -> new SkillEffectDefinition(e.name(), e.time(),
                                        e.power() == null ? 0 : e.power(), e.type(), e.apply().stream()
                                                .map(a -> new StatModifier(a.stat(), a.value(), a.op())).toList()))
                                .toList();
                int reuseTimeMs = Math.round(definition.reuseTime() * 1000f);
                int castingTimeMs = Math.round(definition.castTime() * 1000f);
                ActiveSkill activeSkill = new ActiveSkill(definition.id(), definition.name(), levels, reuseTimeMs,
                        castingTimeMs, definition.range(), aoeRadius, definition.skillType(), target,
                        definition.element() == null ? SkillElement.NONE : definition.element(), projectile,
                        projectileSpeed, effects);
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

    // learnableSkillIds(level) mélange sorts actifs et compétences passives
    // (ex: Expertise Grade) ; seuls les ids présents dans activeSkills nous
    // concernent ici, le reste est géré par PassiveSkillCatalog.
    public List<LearnableSkill> skillsLearnableAt(CharacterClass characterClass, int level) {
        return characterClass.learnableSkillIds(level).stream()
                .filter(learnable -> activeSkills.containsKey(learnable.skillId()))
                .map(learnable -> new LearnableSkill(getById(learnable.skillId()), learnable.level())).toList();
    }

    public record LearnableSkill(ActiveSkill skill, int level) {
    }

    // skills.xml mélange sorts actifs et compétences passives sous la même racine
    // <skills>/<skill> ; skillType==PASSIVE (Expertise Grade) est ignoré ci-dessus
    // et géré par PassiveSkillCatalog à la place.
    private record SkillsDocument(
            @JacksonXmlProperty(localName = "skill") @JacksonXmlElementWrapper(useWrapping = false) List<SkillDefinition> skills) {
    }

    private record SkillDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name,
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<SkillLevelXml> levels,
            Float reuseTime, Float castTime, Integer range, Integer aoeRadius, SkillEffectType skillType,
            SkillTargetType target, SkillElement element, ProjectileXml projectile, EffectsXml effects) {
    }

    // mana/power sont Integer (et non int) car un skill PASSIVE (Expertise Grade)
    // partage ce même schéma <level> mais n'en a pas — cf. warmSkills(), qui
    // saute ces définitions avant de déréférencer mana/power.
    private record SkillLevelXml(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) Integer mana,
            @JacksonXmlProperty(isAttribute = true) Integer power) {
    }

    private record ProjectileXml(@JacksonXmlProperty(isAttribute = true) int speed) {
    }

    // <effects> est un objet imbriqué unique (pas une liste répétée) car
    // @JacksonXmlElementWrapper combiné à un @JacksonXmlProperty(localName=...)
    // différent du nom du composant du record casse la désérialisation
    // (tools.jackson.dataformat.xml) ; on retombe donc sur le même schéma
    // "liste non wrappée avec localName" qui, lui, fonctionne (cf. levels/apply).
    private record EffectsXml(
            @JacksonXmlProperty(localName = "effect") @JacksonXmlElementWrapper(useWrapping = false) List<SkillEffectDefinitionXml> effect) {
    }

    private record SkillEffectDefinitionXml(@JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) int time, @JacksonXmlProperty(isAttribute = true) Integer power,
            @JacksonXmlProperty(isAttribute = true) EffectCategory type,
            @JacksonXmlProperty(localName = "apply") @JacksonXmlElementWrapper(useWrapping = false) List<StatModifierXml> apply) {
    }

    private record StatModifierXml(@JacksonXmlProperty(isAttribute = true) ModifiedStat stat,
            @JacksonXmlProperty(isAttribute = true) int value,
            @JacksonXmlProperty(isAttribute = true) StatOperator op) {
    }
}

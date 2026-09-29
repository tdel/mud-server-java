package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.PassiveSkill;
import app.domain.PassiveSkill.PassiveLevel;
import app.domain.PassiveSkill.PassiveModifier;
import app.domain.SkillEffectType;
import app.domain.StatModifier;
import app.domain.StatOperator;
import app.domain.actor.ModifiedStat;
import app.domain.item.ArmorCategory;
import app.domain.actor.CharacterClass;
import app.domain.item.ItemGrade;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Service
public class PassiveSkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(PassiveSkillCatalog.class);

    private static final String PASSIVE_SKILL_RESOURCE = "/data/skills/skills.xml";

    private final Map<UUID, PassiveSkill> passiveSkills = new ConcurrentHashMap<>();

    private final XmlMapper xmlMapper;

    public PassiveSkillCatalog(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public void warmPassiveSkills() {
        try (InputStream in = getClass().getResourceAsStream(PASSIVE_SKILL_RESOURCE)) {
            SkillsDocument document = xmlMapper.readValue(in, SkillsDocument.class);
            for (SkillDefinition definition : document.skills()) {
                if (definition.skillType() != SkillEffectType.PASSIVE) {
                    continue;
                }
                if (definition.levels() == null || definition.levels().isEmpty()) {
                    throw new IllegalStateException("Compétence passive " + definition.id() + " (" + definition.name()
                            + ") n'a aucun level dans " + PASSIVE_SKILL_RESOURCE);
                }
                // Un level porte soit un grade (value="D", Expertise Grade), soit des
                // <apply> (passif de stats, Weapon Mastery...) — au moins l'un des deux.
                List<PassiveLevel> levels = definition.levels().stream()
                        .map(l -> new PassiveLevel(l.id(), l.value(), l.apply() == null
                                ? List.<PassiveModifier>of()
                                : l.apply().stream()
                                        .map(a -> new PassiveModifier(new StatModifier(a.stat(), a.value(), a.op()),
                                                parseArmor(definition.name(), a.armor()),
                                                parseArmor(definition.name(), a.notArmor())))
                                        .toList()))
                        .toList();
                for (int i = 0; i < levels.size(); i++) {
                    PassiveLevel level = levels.get(i);
                    if (level.level() != i + 1 || level.grade() == null && level.modifiers().isEmpty()) {
                        throw new IllegalStateException("Compétence passive " + definition.id() + " ("
                                + definition.name() + ") a des levels invalides dans " + PASSIVE_SKILL_RESOURCE);
                    }
                }
                PassiveSkill passiveSkill = new PassiveSkill(definition.id(), definition.name(),
                        definition.description() == null ? "" : definition.description().strip(), levels);
                if (passiveSkills.containsKey(passiveSkill.id())) {
                    throw new IllegalStateException("Compétence passive " + passiveSkill.id() + " ("
                            + passiveSkill.name() + ") a un id déjà utilisé par "
                            + passiveSkills.get(passiveSkill.id()).name() + " dans " + PASSIVE_SKILL_RESOURCE);
                }
                passiveSkills.put(passiveSkill.id(), passiveSkill);
            }
            log.info("passive_skill.templates_loaded count={}", passiveSkills.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + PASSIVE_SKILL_RESOURCE, e);
        }
    }

    // armor="LIGHT,HEAVY" / notArmor="ROBE" : liste de types d'armure séparés par
    // des virgules.
    private static Set<ArmorCategory> parseArmor(String skillName, String armor) {
        if (armor == null || armor.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(armor.split(",")).map(String::strip).map(ArmorCategory::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Compétence passive " + skillName + " : type d'armure inconnu \"" + armor
                    + "\" dans " + PASSIVE_SKILL_RESOURCE, e);
        }
    }

    public PassiveSkill getById(UUID passiveSkillId) {
        PassiveSkill passiveSkill = passiveSkills.get(passiveSkillId);
        if (passiveSkill == null) {
            throw new IllegalStateException("Compétence passive " + passiveSkillId
                    + " absente du cache — warmPassiveSkills() a-t-il été appelé ?");
        }
        return passiveSkill;
    }

    public boolean isKnownId(UUID passiveSkillId) {
        return passiveSkills.containsKey(passiveSkillId);
    }

    // Symétrique de SkillCatalog.skillsLearnableAt, pour les compétences
    // passives (ex: Expertise Grade) référencées par une classe.
    public List<LearnablePassiveSkill> passiveSkillsLearnableAt(CharacterClass characterClass, int level) {
        return characterClass.learnableSkillIds(level).stream()
                .filter(learnable -> passiveSkills.containsKey(learnable.skillId()))
                .map(learnable -> new LearnablePassiveSkill(getById(learnable.skillId()), learnable.level())).toList();
    }

    public record LearnablePassiveSkill(PassiveSkill passiveSkill, int level) {
    }

    // skills.xml mélange sorts actifs et compétences passives sous la même racine
    // <skills>/<skill> ; seul <skillType>PASSIVE</skillType> distingue ces
    // dernières (cf. SkillCatalog, qui les ignore symétriquement).
    private record SkillsDocument(
            @JacksonXmlProperty(localName = "skill") @JacksonXmlElementWrapper(useWrapping = false) List<SkillDefinition> skills) {
    }

    private record SkillDefinition(@JacksonXmlProperty(isAttribute = true) UUID id, String name, String description,
            SkillEffectType skillType,
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<LevelXml> levels) {
    }

    private record LevelXml(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) ItemGrade value,
            @JacksonXmlProperty(localName = "apply") @JacksonXmlElementWrapper(useWrapping = false) List<StatModifierXml> apply) {
    }

    private record StatModifierXml(@JacksonXmlProperty(isAttribute = true) ModifiedStat stat,
            @JacksonXmlProperty(isAttribute = true) int value, @JacksonXmlProperty(isAttribute = true) StatOperator op,
            @JacksonXmlProperty(isAttribute = true) String armor,
            @JacksonXmlProperty(isAttribute = true) String notArmor) {
    }
}

package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

// Arbre de compétences d'une classe (data/classes/*.xml, classSkillTree du
// datapack L2J H5) : une entrée par (compétence, level), dans l'ordre du XML.
// L'arbre d'un personnage est la concaténation de celui de sa classe de base et
// de ceux de ses sous-classes (cf. ClassSystem.skillTree) — les levels d'une
// même compétence se suivent d'un arbre à l'autre (Heal 1-6 Mystic, 7-18
// Cleric), comme dans L2.
public record SkillTree(List<Entry> entries) {

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    public record Entry(UUID skillId, int level, int playerLevel, boolean autoGet) {
    }

    public record LearnableSkill(UUID skillId, int level) {
    }

    public static SkillTree load(String resource) {
        try (InputStream in = SkillTree.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Ressource introuvable : " + resource);
            }
            return XML_MAPPER.readValue(in, Document.class).toTree();
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + resource, e);
        }
    }

    public SkillTree plus(SkillTree other) {
        List<Entry> merged = new ArrayList<>(entries);
        merged.addAll(other.entries);
        return new SkillTree(List.copyOf(merged));
    }

    // Seuls les levels autoGet sont appris d'office (création, montée de level,
    // réconciliation au login) ; les autres s'apprennent auprès d'un PNJ
    // SKILL_LEARNER (cf. game.SkillTrainer), comme chez le Grand Master de L2.
    public List<LearnableSkill> autoLearnableAt(int playerLevel) {
        return entries.stream().filter(entry -> entry.autoGet() && entry.playerLevel() == playerLevel)
                .map(entry -> new LearnableSkill(entry.skillId(), entry.level())).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Document(
            @JacksonXmlProperty(localName = "skill") @JacksonXmlElementWrapper(useWrapping = false) List<SkillXml> skill) {
        SkillTree toTree() {
            if (skill == null) {
                return new SkillTree(List.of());
            }
            return new SkillTree(skill.stream()
                    .flatMap(entry -> entry.level().stream()
                            .map(l -> new Entry(entry.id(), l.id(), l.playerLevel(), Boolean.TRUE.equals(l.autoGet()))))
                    .toList());
        }
    }

    private record SkillXml(@JacksonXmlProperty(isAttribute = true) UUID id,
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<LevelXml> level) {
    }

    private record LevelXml(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) int playerLevel,
            @JacksonXmlProperty(isAttribute = true) Boolean autoGet) {
    }
}

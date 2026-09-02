package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import app.game.combat.CombatFormulas;

public enum CharacterClass {
    FIGHTER, MYSTIC;

    private static final String RESOURCE_DIR = "/data/classes/";

    static {
        XmlMapper xmlMapper = new XmlMapper();
        for (CharacterClass characterClass : values()) {
            String resource = RESOURCE_DIR + characterClass.name().toLowerCase() + ".xml";
            try (InputStream in = CharacterClass.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Ressource introuvable : " + resource);
                }
                Json json = xmlMapper.readValue(in, Json.class);
                characterClass.definition = json.toDefinition();
            } catch (IOException | JacksonException e) {
                throw new IllegalStateException("Impossible de charger " + resource, e);
            }
        }
    }

    private Definition definition;

    public List<LearnableSkill> learnableSkillIds(int level) {
        return definition.skills().stream().flatMap(entry -> entry.level().stream()
                .filter(l -> l.playerLevel() == level).map(l -> new LearnableSkill(entry.id(), l.id()))).toList();
    }

    public record LearnableSkill(UUID skillId, int level) {
    }

    // Courbe L2 retail (Human Fighter/Mystic) : cf. data/classes/*.xml et
    // CombatFormulas.maxHealth/maxMana pour la formule.
    public int maxHealth(int constitutionScore, int level) {
        return CombatFormulas.maxHealth(definition.hpBase(), definition.hpAdd(), definition.hpMod(), level,
                constitutionScore);
    }

    public int maxMana(int menScore, int level) {
        return CombatFormulas.maxMana(definition.mpBase(), definition.mpAdd(), definition.mpMod(), level, menScore);
    }

    public Map<Attribute, Integer> baseAttributes() {
        return definition.baseAttributes();
    }

    public String label() {
        return switch (this) {
            case FIGHTER -> "Fighter";
            case MYSTIC -> "Mystic";
        };
    }

    private record Definition(double hpBase, double hpAdd, double hpMod, double mpBase, double mpAdd, double mpMod,
            Map<Attribute, Integer> baseAttributes, List<SkillEntry> skills) {
    }

    private record Json(double hpBase, double hpAdd, double hpMod, double mpBase, double mpAdd, double mpMod,
            Map<Attribute, Integer> baseAttributes,
            @JacksonXmlElementWrapper(useWrapping = false) List<SkillEntry> skill) {
        Definition toDefinition() {
            return new Definition(hpBase, hpAdd, hpMod, mpBase, mpAdd, mpMod, Map.copyOf(baseAttributes),
                    List.copyOf(skill));
        }
    }

    private record SkillEntry(@JacksonXmlProperty(isAttribute = true) UUID id,
            @JacksonXmlProperty(localName = "level") @JacksonXmlElementWrapper(useWrapping = false) List<SkillLevel> level) {
    }

    private record SkillLevel(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) int playerLevel) {
    }
}

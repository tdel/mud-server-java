package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

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
        return definition.skills().stream().filter(skillLevel -> skillLevel.requiredLevel() == level)
                .map(skillLevel -> new LearnableSkill(skillLevel.id(), skillLevel.level())).toList();
    }

    public List<UUID> learnablePassiveSkillIds(int level) {
        return definition.passiveSkills().stream().filter(skillLevel -> skillLevel.requiredLevel() == level)
                .map(SkillLevel::id).toList();
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
            Map<Attribute, Integer> baseAttributes, List<ActiveSkillLevel> skills, List<SkillLevel> passiveSkills) {
    }

    private record Json(double hpBase, double hpAdd, double hpMod, double mpBase, double mpAdd, double mpMod,
            Map<Attribute, Integer> baseAttributes,
            @JacksonXmlElementWrapper(useWrapping = false) List<ActiveSkillLevel> skills,
            @JacksonXmlElementWrapper(useWrapping = false) List<SkillLevel> passiveSkills) {
        Definition toDefinition() {
            return new Definition(hpBase, hpAdd, hpMod, mpBase, mpAdd, mpMod, Map.copyOf(baseAttributes),
                    List.copyOf(skills), List.copyOf(passiveSkills));
        }
    }

    private record SkillLevel(UUID id, int requiredLevel) {
    }

    // Un sort actif se déclinant sur plusieurs levels (1..N), chaque entrée fixe le
    // niveau de personnage requis pour débloquer ce level précis du sort.
    private record ActiveSkillLevel(UUID id, int level, int requiredLevel) {
    }
}

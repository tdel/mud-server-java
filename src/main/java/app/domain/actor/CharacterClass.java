package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import app.game.combat.CombatFormulas;

public enum CharacterClass {
    FIGHTER, MYSTIC;

    private static final String RESOURCE = "/data/class.json";

    static {
        try (InputStream in = CharacterClass.class.getResourceAsStream(RESOURCE)) {
            ObjectMapper objectMapper = new ObjectMapper();
            List<Json> definitions = objectMapper.readValue(in, new TypeReference<List<Json>>() {
            });
            for (Json json : definitions) {
                json.name().definition = json.toDefinition();
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + RESOURCE, e);
        }
    }

    private Definition definition;

    // Courbe L2 retail (Human Fighter/Mystic) : cf. data/class.json et
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
            Map<Attribute, Integer> baseAttributes) {
    }

    private record Json(CharacterClass name, double hpBase, double hpAdd, double hpMod, double mpBase, double mpAdd,
            double mpMod, Map<Attribute, Integer> baseAttributes) {
        Definition toDefinition() {
            return new Definition(hpBase, hpAdd, hpMod, mpBase, mpAdd, mpMod, Map.copyOf(baseAttributes));
        }
    }
}

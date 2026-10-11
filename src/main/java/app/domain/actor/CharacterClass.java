package app.domain.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;

import app.domain.item.EquipmentSlot;

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
                Definition definition = xmlMapper.readValue(in, Definition.class);
                characterClass.baseAttributes = definition.baseAttributes();
                characterClass.baseStats = definition.baseStats().toDomain();
            } catch (IOException | JacksonException e) {
                throw new IllegalStateException("Impossible de charger " + resource, e);
            }
            characterClass.tree = SkillTree.load(resource);
            characterClass.vitals = ClassVitals.load(resource);
        }
    }

    private Map<Attribute, Integer> baseAttributes;
    private ClassBaseStats baseStats;
    private ClassVitals vitals;
    private SkillTree tree;

    // Arbre de la classe de base seule (levels 1-19 + Expertise Grade) ; celui
    // d'un personnage y ajoute ses sous-classes, cf. ClassSystem.skillTree.
    public SkillTree tree() {
        return tree;
    }

    // PV/PM/régénération de base par niveau (Human Fighter/Mystic resté sans
    // profession) ; cf. ClassSystem.vitals pour celle d'un personnage.
    public ClassVitals vitals() {
        return vitals;
    }

    // Poing, défenses nues, vitesses : cf. ClassBaseStats.
    public ClassBaseStats baseStats() {
        return baseStats;
    }

    public Map<Attribute, Integer> baseAttributes() {
        return baseAttributes;
    }

    public String label() {
        return switch (this) {
            case FIGHTER -> "Fighter";
            case MYSTIC -> "Mystic";
        };
    }

    // Les <skill> de l'arbre et <vitals> sont lus à part par SkillTree.load et
    // ClassVitals.load.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Definition(Map<Attribute, Integer> baseAttributes, BaseStatsXml baseStats) {
    }

    private record BaseStatsXml(double pAtk, double mAtk, int atkSpd, double critRate, double runSpeed, PDefXml pDef,
            MDefXml mDef) {

        ClassBaseStats toDomain() {
            Map<EquipmentSlot, Double> pDefBySlot = new EnumMap<>(EquipmentSlot.class);
            pDefBySlot.put(EquipmentSlot.CHEST, pDef.chest());
            pDefBySlot.put(EquipmentSlot.LEGS, pDef.legs());
            pDefBySlot.put(EquipmentSlot.HEAD, pDef.head());
            pDefBySlot.put(EquipmentSlot.FEET, pDef.feet());
            pDefBySlot.put(EquipmentSlot.HANDS, pDef.gloves());
            Map<EquipmentSlot, Double> mDefBySlot = new EnumMap<>(EquipmentSlot.class);
            mDefBySlot.put(EquipmentSlot.RIGHT_EARRING, mDef.rear());
            mDefBySlot.put(EquipmentSlot.LEFT_EARRING, mDef.lear());
            mDefBySlot.put(EquipmentSlot.RIGHT_RING, mDef.rfinger());
            mDefBySlot.put(EquipmentSlot.LEFT_RING, mDef.lfinger());
            mDefBySlot.put(EquipmentSlot.NECKLACE, mDef.neck());
            return new ClassBaseStats(pAtk, mAtk, atkSpd, critRate, runSpeed, pDefBySlot,
                    pDef.underwear() + pDef.cloak(), mDefBySlot);
        }
    }

    private record PDefXml(double chest, double legs, double head, double feet, double gloves, double underwear,
            double cloak) {
    }

    private record MDefXml(double rear, double lear, double rfinger, double lfinger, double neck) {
    }
}

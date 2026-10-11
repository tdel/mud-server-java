package app.domain.actor;

import java.util.Arrays;
import java.util.List;

// Professions humaines de Lineage 2 High Five : 1re profession au level 20
// (tier 1, depuis Human Fighter/Mystic), 2e profession au level 40 (tier 2,
// depuis la 1re). Chacune apporte son arbre de compétences
// (data/classes/<nom>.xml, classSkillTree L2J), ajouté à ceux de ses parents
// (cf. ClassSystem.skillTree). Les noms d'enum WARRIOR..CLERIC sont persistés
// tels quels (character.subclass_tier1/2) depuis l'ancien système à 5
// sous-classes : ne pas les renommer.
public enum Subclass {
    WARRIOR(CharacterClass.FIGHTER, null, "Warrior",
            "Combattant offensif : armes d'hast et épées, coups étourdissants, attaques de zone."), KNIGHT(
                    CharacterClass.FIGHTER, null, "Human Knight",
                    "Défenseur en armure lourde et bouclier : provocation, étourdissement au bouclier, soins divins."), ROGUE(
                            CharacterClass.FIGHTER, null, "Rogue",
                            "Combattant agile : dague et arc, coups mortels, saignements et esquive."), WIZARD(
                                    CharacterClass.MYSTIC, null, "Human Wizard",
                                    "Mage offensif : sorts de feu, de vent et des ténèbres, malédictions et invocation de chats."), CLERIC(
                                            CharacterClass.MYSTIC, null, "Cleric",
                                            "Prêtre : soins, résurrection, bénédictions et entraves (sommeil, racines)."), GLADIATOR(
                                                    CharacterClass.FIGHTER, WARRIOR, "Gladiator",
                                                    "Maître des épées doubles et des coups dévastateurs au corps à corps."), WARLORD(
                                                            CharacterClass.FIGHTER, WARRIOR, "Warlord",
                                                            "Seigneur de guerre à la lance : tourbillons et tempêtes qui frappent tous les ennemis autour."), PALADIN(
                                                                    CharacterClass.FIGHTER, KNIGHT, "Paladin",
                                                                    "Chevalier sacré : aura de haine, bénédictions divines et soins puissants."), DARK_AVENGER(
                                                                            CharacterClass.FIGHTER, KNIGHT,
                                                                            "Dark Avenger",
                                                                            "Chevalier des ténèbres : drain de vie, renvoi des dégâts et panthère noire invoquée."), TREASURE_HUNTER(
                                                                                    CharacterClass.FIGHTER, ROGUE,
                                                                                    "Treasure Hunter",
                                                                                    "Assassin à la dague : coups dans le dos, pas de l'ombre, mort feinte et marche silencieuse."), HAWKEYE(
                                                                                            CharacterClass.FIGHTER,
                                                                                            ROGUE, "Hawkeye",
                                                                                            "Archer d'élite : doubles tirs, tirs étourdissants et œil de faucon."), SORCERER(
                                                                                                    CharacterClass.MYSTIC,
                                                                                                    WIZARD, "Sorcerer",
                                                                                                    "Mage de feu : Prominence, Aura Flare, cercle de flammes et sommeil."), NECROMANCER(
                                                                                                            CharacterClass.MYSTIC,
                                                                                                            WIZARD,
                                                                                                            "Necromancer",
                                                                                                            "Nécromancien : griffes vampiriques, malédictions (peur, silence, discorde) et mort-vivant invoqué."), WARLOCK(
                                                                                                                    CharacterClass.MYSTIC,
                                                                                                                    WIZARD,
                                                                                                                    "Warlock",
                                                                                                                    "Invocateur : chats de combat, cubiques et soins de serviteur."), BISHOP(
                                                                                                                            CharacterClass.MYSTIC,
                                                                                                                            CLERIC,
                                                                                                                            "Bishop",
                                                                                                                            "Grand soigneur : soins majeurs, résurrection de groupe et requiem contre les morts-vivants."), PROPHET(
                                                                                                                                    CharacterClass.MYSTIC,
                                                                                                                                    CLERIC,
                                                                                                                                    "Prophet",
                                                                                                                                    "Prophète : bénédictions de combat (guidance, murmure de mort), racines et retour en ville.");

    private static final String RESOURCE_DIR = "/data/classes/";

    private final CharacterClass baseClass;
    private final Subclass parent;
    private final String label;
    private final String description;
    private SkillTree tree;
    private ClassVitals vitals;

    Subclass(CharacterClass baseClass, Subclass parent, String label, String description) {
        this.baseClass = baseClass;
        this.parent = parent;
        this.label = label;
        this.description = description;
    }

    static {
        for (Subclass subclass : values()) {
            String resource = RESOURCE_DIR + subclass.name().toLowerCase() + ".xml";
            subclass.tree = SkillTree.load(resource);
            subclass.vitals = ClassVitals.load(resource);
        }
    }

    // PV/PM/régénération de base par niveau de la profession (table L2, cf.
    // ClassVitals).
    public ClassVitals vitals() {
        return vitals;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public CharacterClass baseClass() {
        return baseClass;
    }

    public Subclass parent() {
        return parent;
    }

    // 1 = 1re profession (level 20), 2 = 2e profession (level 40).
    public int tier() {
        return parent == null ? 1 : 2;
    }

    // Compétences propres à cette profession (sans celles des parents).
    public SkillTree tree() {
        return tree;
    }

    // Professions accessibles à un personnage de classe `baseClass` dont la
    // dernière profession choisie est `current` (null : aucune) — vide une fois
    // la 2e profession prise.
    public static List<Subclass> availableAfter(CharacterClass baseClass, Subclass current) {
        return Arrays.stream(values()).filter(subclass -> subclass.baseClass == baseClass && subclass.parent == current)
                .toList();
    }
}

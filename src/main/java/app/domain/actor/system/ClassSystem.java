package app.domain.actor.system;

import java.util.ArrayList;
import java.util.List;

import app.domain.actor.Attribute;
import app.domain.actor.CharacterClass;
import app.domain.actor.ClassVitals;
import app.domain.actor.SkillTree;
import app.domain.actor.Subclass;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.SubclassChosen;

public final class ClassSystem {

    // Changements de profession L2 : 1re au level 20, 2e au level 40.
    public static final int TIER1_LEVEL = 20;
    public static final int TIER2_LEVEL = 40;

    private final PlayerInstance character;
    private final CharacterClass characterClass;
    private final List<Subclass> subclasses = new ArrayList<>();

    public ClassSystem(PlayerInstance character, CharacterClass characterClass, List<Subclass> subclasses) {
        this.character = character;
        this.characterClass = characterClass;
        this.subclasses.addAll(subclasses);
    }

    public CharacterClass getCharacterClass() {
        return characterClass;
    }

    public List<Subclass> getSubclasses() {
        return List.copyOf(subclasses);
    }

    public Subclass getSubclass(int tier) {
        return tier >= 1 && tier <= subclasses.size() ? subclasses.get(tier - 1) : null;
    }

    // Sous-classe du palier le plus élevé choisi (celle qu'affiche la fenêtre de
    // groupe), null tant qu'aucune n'a été choisie.
    public Subclass getCurrentSubclass() {
        return subclasses.isEmpty() ? null : subclasses.get(subclasses.size() - 1);
    }

    // Nom L2 de la profession actuelle (Human Fighter... Sorcerer).
    public String currentClassLabel() {
        Subclass current = getCurrentSubclass();
        return current != null ? current.label() : characterClass.label();
    }

    // Table PV/PM/régénération de la profession actuelle (celle de la classe de
    // base avant le level 20) : en L2, changer de profession fait passer sur sa
    // table au niveau courant.
    public ClassVitals vitals() {
        return vitalsOf(characterClass, subclasses);
    }

    public static ClassVitals vitalsOf(CharacterClass characterClass, List<Subclass> subclasses) {
        return subclasses.isEmpty() ? characterClass.vitals() : subclasses.get(subclasses.size() - 1).vitals();
    }

    // PV/PM max de base (hors passifs/buffs) au niveau courant : table de la
    // classe x CON / MEN (FuncMaxHpMul/FuncMaxMpMul).
    public static int maxHealth(ClassVitals vitals, int level, int constitutionScore) {
        return CombatFormulas.maxHealth(vitals.at(level).hp(), constitutionScore);
    }

    public static int maxMana(ClassVitals vitals, int level, int menScore) {
        return CombatFormulas.maxMana(vitals.at(level).mp(), menScore);
    }

    // Recalcule les PV/PM max après un changement de niveau ou de profession ;
    // les PV/PM courants gagnent l'écart (jamais en dessous de 1/0).
    public void refreshVitals() {
        int level = character.getLevelingSystem().getLevel();
        ResourceSystem resources = character.getResourceSystem();
        int newMaxHealth = maxHealth(vitals(), level, character.getAttributeSystem().getAttribute(Attribute.CON));
        int hpGain = newMaxHealth - resources.getBaseMaxHealth();
        resources.setMaxHealth(newMaxHealth);
        resources.setCurrentHealth(Math.max(1, resources.getCurrentHealth() + hpGain));
        int newMaxMana = maxMana(vitals(), level, character.getAttributeSystem().getAttribute(Attribute.MEN));
        int manaGain = newMaxMana - resources.getBaseMaxMana();
        resources.setMaxMana(newMaxMana);
        resources.setCurrentMana(Math.max(0, resources.getCurrentMana() + manaGain));
    }

    // Arbre complet du personnage : classe de base puis chaque profession prise,
    // dans l'ordre (cf. SkillTree).
    public SkillTree skillTree() {
        SkillTree tree = characterClass.tree();
        for (Subclass subclass : subclasses) {
            tree = tree.plus(subclass.tree());
        }
        return tree;
    }

    // Professions proposées au palier suivant (vides si aucune).
    public List<Subclass> nextOptions() {
        return Subclass.availableAfter(characterClass, getCurrentSubclass());
    }

    // Level requis pour le prochain changement de profession, null s'il n'y en a
    // plus.
    public Integer nextChangeLevel() {
        return switch (subclasses.size()) {
            case 0 -> TIER1_LEVEL;
            case 1 -> TIER2_LEVEL;
            default -> null;
        };
    }

    public Integer getPendingSubclassTier() {
        Integer requiredLevel = nextChangeLevel();
        if (requiredLevel == null || character.getLevelingSystem().getLevel() < requiredLevel) {
            return null;
        }
        return subclasses.size() + 1;
    }

    public void chooseSubclass(Subclass subclass) {
        Integer tier = getPendingSubclassTier();
        if (tier == null || !nextOptions().contains(subclass)) {
            throw new IllegalStateException("Choix de sous-classe invalide: " + subclass + " (tier=" + tier
                    + ", classe=" + characterClass + ", actuelle=" + getCurrentSubclass() + ")");
        }
        subclasses.add(subclass);
        refreshVitals();
        character.send(new SubclassChosen(tier, subclass, subclass.label()));
        DomainEventPublisher.publish(new CharacterChoseSubclass(character, tier, subclass));
    }
}

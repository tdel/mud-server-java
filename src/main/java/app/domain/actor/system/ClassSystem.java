package app.domain.actor.system;

import java.util.ArrayList;
import java.util.List;

import app.domain.actor.CharacterClass;
import app.domain.actor.Subclass;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.CharacterInstance;

public final class ClassSystem {

    // tier 1 = niveau 20, tier 2 = niveau 40 ; à compléter le jour où un tier 3
    // existe (cf. Subclass.availableAt).
    private static final int TIER1_LEVEL = 20;
    private static final int TIER2_LEVEL = 40;

    private final CharacterInstance character;
    private final CharacterClass characterClass;
    private final List<Subclass> subclasses = new ArrayList<>();

    public ClassSystem(CharacterInstance character, CharacterClass characterClass, List<Subclass> subclasses) {
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

    public Integer getPendingSubclassTier() {
        int nextTier = subclasses.size() + 1;
        if (nextTier == 1 && character.getLevel() >= TIER1_LEVEL) {
            return 1;
        }
        if (nextTier == 2 && character.getLevel() >= TIER2_LEVEL) {
            return 2;
        }
        return null;
    }

    public void chooseSubclass(Subclass subclass) {
        Integer tier = getPendingSubclassTier();
        if (tier == null || !Subclass.availableAt(characterClass, tier).contains(subclass)) {
            throw new IllegalStateException("Choix de sous-classe invalide: " + subclass + " (tier=" + tier
                    + ", classe=" + characterClass + ")");
        }
        subclasses.add(subclass);
        DomainEventPublisher.publish(new CharacterChoseSubclass(character, tier, subclass));
    }
}

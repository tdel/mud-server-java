package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import app.domain.ActiveEffect;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;

public final class InventorySystem {

    private static final UUID GRADE_PENALTY_EFFECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CharacterInstance character;
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int gold;

    public InventorySystem(CharacterInstance character, int gold) {
        this.character = character;
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

    public void addGold(int amount) {
        this.gold += amount;
    }

    public boolean trySpendGold(int amount) {
        if (gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Optional<Item> findOneById(UUID id) {
        return items.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public List<Item> getEquippedItems() {
        return items.stream().filter(item -> item.getSlot() != null).toList();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
    }

    public void replaceItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
    }

    public void receiveGold(int amount) {
        addGold(amount);
        DomainEventPublisher.publish(new CharacterReceivedGold(character, amount));
    }

    public void receiveLootItem(Item item) {
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new CharacterLootedItem(character, item));
    }

    public boolean buyItem(Item item, int price) {
        if (!trySpendGold(price)) {
            return false;
        }
        DomainEventPublisher.publish(new CharacterSpentGold(character, price));
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new ItemPurchased(character, item, price));
        return true;
    }

    public void discardItem(Item item) {
        removeItem(item);
        DomainEventPublisher.publish(new ItemDiscarded(character, item));
    }

    public Optional<EquipmentSlot> equipItem(Item item) {
        List<EquipmentSlot> candidates = item.getType().equipmentSlots();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Item> equipped = getEquippedItems();
        EquipmentSlot slot = candidates.stream()
                .filter(candidate -> equipped.stream().noneMatch(existing -> existing.getSlot() == candidate))
                .findFirst().orElse(candidates.get(0));

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : equipped) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot);
        DomainEventPublisher.publish(new GamePlayerEquippedItem(character, item, slot, previousOccupants));
        recomputeGradePenalty();
        return Optional.of(slot);
    }

    public void unequipItem(Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(character, item));
        recomputeGradePenalty();
    }

    // Marqueur unique dans ActiveEffects tant qu'au moins un objet équipé dépasse
    // le grade débloqué par les compétences passives connues (SkillSystem). Pas
    // de vraie expiration (le malus dure tant que l'objet reste équipé) : on
    // recalcule/rafraîchit à chaque equip/unequip plutôt que de s'appuyer sur un
    // minuteur. ModifiedStat.PATK/amount=-1 est un placeholder inerte, requis
    // uniquement pour que ActiveEffect.category() retombe côté DEBUFF — le vrai
    // calcul des malus par stat (p.atk, atk.spd, évasion, vitesse, régénération...)
    // sera implémenté séparément, en lisant l'équipement + l'expertise directement
    // plutôt que la valeur de cet effet.
    public void recomputeGradePenalty() {
        boolean overGraded = getEquippedItems().stream()
                .anyMatch(item -> item.getGrade().ordinal() > character.getSkillSystem().unlockedGrade().ordinal());
        if (overGraded) {
            character.getEffectsSystem().apply(new ActiveEffect(GRADE_PENALTY_EFFECT_ID, "Grade Penalty",
                    ModifiedStat.PATK, -1, Instant.now().plus(Duration.ofDays(3650))));
        } else {
            character.getEffectsSystem().remove(GRADE_PENALTY_EFFECT_ID);
        }
    }
}

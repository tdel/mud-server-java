package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import app.domain.ActiveEffect;
import app.domain.StatModifier;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentItem;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemExpectation;

public final class InventorySystem {

    private static final UUID GRADE_PENALTY_EFFECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CharacterInstance character;
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int gold;

    public InventorySystem(CharacterInstance character, int gold, List<Item> items) {
        this.character = character;
        this.gold = gold;
        this.items.addAll(items);
        items.forEach(item -> item.attachOwner(this.character));
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

    public Optional<Item> getEquippedWeapon() {
        return getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
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
        character.recomputeStats();
        return Optional.of(slot);
    }

    public void unequipItem(Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(character, item));
        recomputeGradePenalty();
        character.recomputeStats();
    }

    // Marqueur unique dans ActiveEffects tant qu'au moins un objet équipé a un
    // ItemExpectation (cf. EquipmentItem.getExpectation) non rempli — typiquement
    // un grade au-delà de l'expertise débloquée (SkillSystem.passiveLevelOf). Pas
    // de vraie expiration (le malus dure tant que l'objet reste équipé) : on
    // recalcule/rafraîchit à chaque equip/unequip plutôt que de s'appuyer sur un
    // minuteur. Les actions des ItemExpectation non remplies sont fusionnées en un
    // seul ActiveEffect (id fixe) plutôt qu'un effet par objet.
    public void recomputeGradePenalty() {
        List<ItemExpectation.ExpectationEffect> unmetActions = getEquippedItems().stream().map(Item::getTemplate)
                .filter(EquipmentItem.class::isInstance).map(EquipmentItem.class::cast)
                .map(EquipmentItem::getExpectation).filter(Objects::nonNull)
                .filter(expectation -> !isSatisfied(expectation)).flatMap(expectation -> expectation.actions().stream())
                .toList();

        if (unmetActions.isEmpty()) {
            character.getEffectsSystem().remove(GRADE_PENALTY_EFFECT_ID);
            return;
        }

        List<StatModifier> modifiers = unmetActions.stream().flatMap(action -> action.modifiers().stream()).toList();
        Duration duration = unmetActions.stream().map(ItemExpectation.ExpectationEffect::duration)
                .max(Duration::compareTo).orElse(Duration.ZERO);
        character.getEffectsSystem().apply(new ActiveEffect(GRADE_PENALTY_EFFECT_ID, unmetActions.get(0).name(),
                modifiers, Instant.now().plus(duration)));
    }

    private boolean isSatisfied(ItemExpectation expectation) {
        return expectation.conditions().stream().allMatch(
                condition -> character.getSkillSystem().passiveLevelOf(condition.skillId()) >= condition.level());
    }
}

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
import app.domain.actor.event.ShotActivated;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.EquipmentItem;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemExpectation;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;

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

    // Un item stackable (soulshot/spiritshot) n'a qu'une seule pile par type+grade
    // dans l'inventaire — jamais équipé, donc getSlot() == null suffit à
    // identifier la pile.
    public Optional<Item> findStackable(ItemType type, ItemGrade grade) {
        return items.stream()
                .filter(item -> item.getSlot() == null && item.getType() == type && item.getGrade() == grade)
                .findFirst();
    }

    public void receiveGold(int amount) {
        addGold(amount);
        DomainEventPublisher.publish(new CharacterReceivedGold(character, amount));
    }

    public void receiveLootItem(Item item) {
        Optional<Item> stack = mergeIntoExistingStack(item);
        if (stack.isPresent()) {
            DomainEventPublisher.publish(new CharacterLootedItem(character, stack.get(), true));
            return;
        }
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new CharacterLootedItem(character, item, false));
    }

    public boolean buyItem(Item item, int price) {
        if (!trySpendGold(price)) {
            return false;
        }
        DomainEventPublisher.publish(new CharacterSpentGold(character, price));
        Optional<Item> stack = mergeIntoExistingStack(item);
        if (stack.isPresent()) {
            DomainEventPublisher.publish(new ItemPurchased(character, stack.get(), price, true));
            return true;
        }
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new ItemPurchased(character, item, price, false));
        return true;
    }

    // Fusionne un item stackable fraîchement acquis (loot/achat) dans une pile
    // existante s'il y en a déjà une — sinon laisse l'appelant l'ajouter comme
    // nouvel item. Retourne la pile mise à jour si un merge a eu lieu.
    private Optional<Item> mergeIntoExistingStack(Item item) {
        if (!item.getType().stackable()) {
            return Optional.empty();
        }
        return findStackable(item.getType(), item.getGrade()).map(existing -> {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            return existing;
        });
    }

    // Nombre de charges consommées par activation : porté par l'arme équipée
    // (cf. EquipmentItem.getShotConsumption), à mains nues 1 charge — jamais une
    // constante fixe, cf. plan Soulshot/Spiritshot.
    public ConsumeShotOutcome consumeShot(ItemType shotType, ItemGrade grade) {
        Optional<Item> stack = findStackable(shotType, grade);
        if (stack.isEmpty()) {
            return new ConsumeShotOutcome.OutOfStock();
        }
        Item item = stack.get();
        int count = getEquippedWeapon().map(Item::getShotConsumption).orElse(1);
        if (item.getQuantity() < count) {
            return new ConsumeShotOutcome.OutOfStock();
        }
        int remaining = item.getQuantity() - count;
        if (remaining <= 0) {
            removeItem(item);
        } else {
            item.setQuantity(remaining);
        }
        DomainEventPublisher.publish(new ShotActivated(character, item, shotType, grade, Math.max(0, remaining)));
        return new ConsumeShotOutcome.Consumed(item, count);
    }

    public sealed interface ConsumeShotOutcome {

        record Consumed(Item item, int count) implements ConsumeShotOutcome {
        }

        record OutOfStock() implements ConsumeShotOutcome {
        }
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

package fr.idev.mudserver.domain.actor.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.ArmorProficiency;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.InventoryComponent;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemDiscarded;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.item.Item;

public final class InventorySystem {

    private InventorySystem() {
    }

    public static void addGold(CharacterInstance character, int amount) {
        character.updateComponent(InventoryComponent.class,
                current -> new InventoryComponent(current.items(), current.gold() + amount));
    }

    public static boolean trySpendGold(CharacterInstance character, int amount) {
        boolean[] spent = {false};
        character.updateComponent(InventoryComponent.class, current -> {
            if (current.gold() < amount) {
                return current;
            }
            spent[0] = true;
            return new InventoryComponent(current.items(), current.gold() - amount);
        });
        return spent[0];
    }

    public static void addItem(CharacterInstance character, Item item) {
        character.updateComponent(InventoryComponent.class, current -> {
            List<Item> newItems = new ArrayList<>(current.items());
            newItems.add(item);
            return new InventoryComponent(List.copyOf(newItems), current.gold());
        });
    }

    public static void removeItem(CharacterInstance character, Item item) {
        character.updateComponent(InventoryComponent.class, current -> {
            List<Item> newItems = new ArrayList<>(current.items());
            newItems.remove(item);
            return new InventoryComponent(List.copyOf(newItems), current.gold());
        });
    }

    public static void replaceItems(CharacterInstance character, List<Item> newItems) {
        character.updateComponent(InventoryComponent.class,
                current -> new InventoryComponent(List.copyOf(newItems), current.gold()));
    }

    public static void receiveGold(CharacterInstance character, int amount) {
        addGold(character, amount);
        DomainEventPublisher.publish(new CharacterReceivedGold(character, amount));
    }

    public static void receiveLootItem(CharacterInstance character, Item item) {
        item.setCharacter(character);
        addItem(character, item);
        DomainEventPublisher.publish(new CharacterLootedItem(character, item));
    }

    public static boolean buyItem(CharacterInstance character, Item item, int price) {
        if (!trySpendGold(character, price)) {
            return false;
        }
        DomainEventPublisher.publish(new CharacterSpentGold(character, price));
        item.setCharacter(character);
        addItem(character, item);
        DomainEventPublisher.publish(new ItemPurchased(character, item, price));
        return true;
    }

    public static Optional<EquipmentSlot> equip(CharacterInstance character, Item item) {
        Optional<EquipmentSlot> slot = item.getType().equipmentSlot();

        if (slot.isEmpty()) {
            return Optional.empty();
        }

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : component(character).equippedItems()) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot.get()) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot.get());
        DomainEventPublisher.publish(new GamePlayerEquippedItem(character, item, slot.get(), previousOccupants));
        return slot;
    }

    public static void unequip(CharacterInstance character, Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(character, item));
    }

    public static void discard(CharacterInstance character, Item item) {
        removeItem(character, item);
        DomainEventPublisher.publish(new ItemDiscarded(character, item));
    }

    public static int getArmorClass(AbstractCharacter character) {
        return switch (character) {
            case CharacterInstance player -> playerArmorClass(player);
            case MonsterInstance monster -> monsterArmorClass(monster);
            default -> baseArmorClass(character);
        };
    }

    public static boolean isWearingNonProficientArmor(CharacterInstance character) {
        return component(character).equippedItems().stream().map(InventorySystem::requiredArmorProficiency).anyMatch(
                required -> required.isPresent() && !character.getArmorProficiencies().contains(required.get()));
    }

    public static Optional<Item> equippedWeapon(CharacterInstance character) {
        return component(character).equippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON)
                .findFirst();
    }

    private static int playerArmorClass(CharacterInstance character) {
        int ac = component(character).equippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST)
                .findFirst().map(item -> armorAc(character, item)).orElseGet(() -> baseArmorClass(character));

        return ac
                + component(character).equippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.OFF_HAND)
                        .mapToInt(item -> item.getBaseAc() + item.getBonus()).sum();
    }

    private static int monsterArmorClass(MonsterInstance monster) {
        if (monster.getTemplate() == null) {
            throw new IllegalStateException("MonsterInstance " + monster.getId() + " has no MonsterTemplate attached");
        }
        Integer natural = monster.getTemplate().getNaturalArmorClass();
        return natural != null ? natural : baseArmorClass(monster);
    }

    private static int baseArmorClass(AbstractCharacter character) {
        return 10 + AttributeSystem.getModifier(character, Attribute.DEXTERITY);
    }

    private static int armorAc(CharacterInstance character, Item armor) {
        int dexMod = AttributeSystem.getModifier(character, Attribute.DEXTERITY);
        int baseAndBonus = armor.getBaseAc() + armor.getBonus();
        return switch (armor.getArmorCategory()) {
            case LIGHT -> baseAndBonus + dexMod;
            case MEDIUM -> baseAndBonus + Math.min(dexMod, 2);
            case HEAVY -> baseAndBonus;
        };
    }

    private static Optional<ArmorProficiency> requiredArmorProficiency(Item item) {
        return switch (item.getType()) {
            case ARMOR, HELMET, PANTS, BOOTS, GLOVES -> Optional.of(ArmorProficiency.of(item.getArmorCategory()));
            case SHIELD -> Optional.of(ArmorProficiency.SHIELDS);
            default -> Optional.empty();
        };
    }

    private static InventoryComponent component(CharacterInstance character) {
        return character.component(InventoryComponent.class);
    }
}

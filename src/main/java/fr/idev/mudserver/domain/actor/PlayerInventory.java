package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.Item;

/**
 * Regroupe les items portés par un {@link GamePlayer} et son or — l'or est figé
 * à la construction (roulé selon la classe à la création du personnage, voir
 * {@code GameWorld.createCharacter}) : aucune boutique/butin n'existe encore
 * pour le faire varier, donc pas de mutateur spéculatif ici.
 */
public final class PlayerInventory {

    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final int gold;

    public PlayerInventory(int gold) {
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Optional<Item> findOneByName(String name) {
        return items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<Item> getCarriedItems() {
        return items.stream().filter(item -> item.getSlot() == null).toList();
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
}

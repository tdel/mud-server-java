package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.Item;

/**
 * Regroupe les items portés par un {@link GamePlayer} et son or — l'or initial
 * est roulé selon la classe à la création du personnage (voir
 * {@code WorldInstance.createCharacter}), puis varie en jeu via
 * {@link #addGold} (butin, voir {@code game.actor.LootService}) ou
 * {@link #trySpendGold} (achat auprès d'un PNJ marchand, voir
 * {@code controller.ingame.Talk}).
 */
public final class PlayerInventory {

    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int gold;

    public PlayerInventory(int gold) {
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

    public void addGold(int amount) {
        this.gold += amount;
    }

    /**
     * Retourne {@code false} sans rien muter si le solde est insuffisant, plutôt
     * que de lever une exception : l'appelant ({@link GamePlayer#buyItem}) réagit
     * ainsi normalement (message « pas assez d'or ») sans try/catch, même style que
     * {@link GamePlayer#pickUpItem}.
     */
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

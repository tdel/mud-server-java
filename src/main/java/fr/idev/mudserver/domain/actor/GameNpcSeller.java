package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.Item;

/**
 * Seul sous-type de {@link GameNpc} : un PNJ marchand, dès que sa
 * {@code NpcDialogue} contient une option {@code SHOP} (invariant posé par
 * {@code NpcService.loadNpcs}, qui choisit ce type plutôt que {@code GameNpc}
 * exactement dans ce cas). Porte lui-même la résolution du catalogue et
 * l'achat, sur le même principe que {@link GamePlayer#pickUpItem}/
 * {@link GamePlayer#equipItem} : le comportement vit sur l'objet de domaine
 * concerné plutôt que dans {@code controller.ingame.Talk}.
 */
public final class GameNpcSeller extends GameNpc {

    private final NpcShop shop;

    public GameNpcSeller(UUID id, String name, UUID roomId, String description, NpcDialogue dialogue, NpcShop shop) {
        super(id, name, roomId, description, dialogue);
        this.shop = Objects.requireNonNull(shop);
    }

    public NpcShop shop() {
        return shop;
    }

    /**
     * Résout une entrée par index 1-based (position dans le catalogue) ou, à
     * défaut, par nom d'article insensible à la casse — même règle que
     * {@link NpcDialogue#resolveOption} pour un choix de dialogue, appliquée ici au
     * catalogue plutôt qu'aux options.
     */
    public Optional<NpcShopEntry> resolveEntry(String input) {
        String trimmed = input.trim();
        try {
            int index = Integer.parseInt(trimmed);
            if (index >= 1 && index <= shop.items().size()) {
                return Optional.of(shop.items().get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // pas un index numérique : recherche par nom ci-dessous
        }
        return shop.items().stream().filter(entry -> entry.itemName().equalsIgnoreCase(trimmed)).findFirst();
    }

    /**
     * Construit l'{@link Item} acheté exactement comme {@code game.actor
     * .LootService} construit un item de butin, puis délègue le débit d'or et
     * l'ajout à l'inventaire à {@link GamePlayer#buyItem} —
     * {@code controller.ingame.Talk} n'a donc plus à connaître la forme d'un item
     * acheté ni la logique de résolution du catalogue.
     */
    public PurchaseOutcome sell(GamePlayer buyer, String input) {
        Optional<NpcShopEntry> entry = resolveEntry(input);
        if (entry.isEmpty()) {
            return new PurchaseOutcome.EntryNotFound();
        }

        Item item = new Item(UUID.randomUUID(), entry.get().itemTemplateId(), null, buyer.getId(), null);
        boolean bought = buyer.buyItem(item, entry.get().price());
        return bought
                ? new PurchaseOutcome.Purchased(item, entry.get().price())
                : new PurchaseOutcome.InsufficientGold(entry.get().price());
    }

    public record NpcShop(List<NpcShopEntry> items) {
    }

    public record NpcShopEntry(UUID itemTemplateId, String itemName, int price) {
    }

    public sealed interface PurchaseOutcome {

        record Purchased(Item item, int price) implements PurchaseOutcome {
        }

        record EntryNotFound() implements PurchaseOutcome {
        }

        record InsufficientGold(int price) implements PurchaseOutcome {
        }
    }
}

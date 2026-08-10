package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.GameNpc.NpcDialogue;
import fr.idev.mudserver.domain.actor.GameNpcSeller.NpcShop;
import fr.idev.mudserver.domain.actor.GameNpcSeller.NpcShopEntry;
import fr.idev.mudserver.domain.actor.GameNpcSeller.PurchaseOutcome;
import fr.idev.mudserver.domain.Rarity;

import static org.assertj.core.api.Assertions.assertThat;

class GameNpcSellerTest {

    private static final UUID POTION_ID = UUID.randomUUID();
    private static final UUID SWORD_ID = UUID.randomUUID();

    @Test
    void resolveEntryByOneBasedIndex() {
        GameNpcSeller seller = seller();

        assertThat(seller.resolveEntry("1")).contains(new NpcShopEntry(POTION_ID, "Potion de soin", Rarity.COMMON, 50));
        assertThat(seller.resolveEntry("2")).contains(new NpcShopEntry(SWORD_ID, "Epée courte", Rarity.COMMON, 10));
    }

    @Test
    void resolveEntryByIndexOutOfRangeIsEmpty() {
        GameNpcSeller seller = seller();

        assertThat(seller.resolveEntry("0")).isEmpty();
        assertThat(seller.resolveEntry("3")).isEmpty();
    }

    @Test
    void resolveEntryByExactNameIsCaseInsensitive() {
        GameNpcSeller seller = seller();

        assertThat(seller.resolveEntry("epée courte"))
                .contains(new NpcShopEntry(SWORD_ID, "Epée courte", Rarity.COMMON, 10));
        assertThat(seller.resolveEntry("POTION DE SOIN"))
                .contains(new NpcShopEntry(POTION_ID, "Potion de soin", Rarity.COMMON, 50));
    }

    @Test
    void resolveEntryByUnknownNameIsEmpty() {
        GameNpcSeller seller = seller();

        assertThat(seller.resolveEntry("Bouclier")).isEmpty();
    }

    @Test
    void sellReturnsEntryNotFoundWhenTheInputMatchesNothing() {
        GameNpcSeller seller = seller();
        GamePlayer buyer = buyer(100);

        assertThat(seller.sell(buyer, "Bouclier")).isInstanceOf(PurchaseOutcome.EntryNotFound.class);
        assertThat(buyer.getInventory().getItems()).isEmpty();
        assertThat(buyer.getInventory().getGold()).isEqualTo(100);
    }

    // Ne teste pas le cas PurchaseOutcome.Purchased ici : il passe par
    // GamePlayer#buyItem, qui publie CharacterSpentGold/ItemPurchased via
    // DomainEventPublisher — suppose un contexte Spring initialisé (voir sa
    // Javadoc), absent de ce test unitaire pur. Déjà couvert de bout en bout par
    // CharacterServiceTest#spendingGoldPersistsItAndNotifiesTheCharacterOnly et
    // ItemServiceTest#buyItemSpendsGoldAndPersistsTheNewItem, qui exercent le même
    // chemin via GamePlayer#buyItem directement.
    @Test
    void sellReturnsInsufficientGoldWhenTheBuyerCannotAffordIt() {
        GameNpcSeller seller = seller();
        GamePlayer buyer = buyer(5);

        PurchaseOutcome outcome = seller.sell(buyer, "1");

        assertThat(outcome).isEqualTo(new PurchaseOutcome.InsufficientGold(50));
        assertThat(buyer.getInventory().getItems()).isEmpty();
        assertThat(buyer.getInventory().getGold()).isEqualTo(5);
    }

    private GameNpcSeller seller() {
        NpcShop shop = new NpcShop(List.of(new NpcShopEntry(POTION_ID, "Potion de soin", Rarity.COMMON, 50),
                new NpcShopEntry(SWORD_ID, "Epée courte", Rarity.COMMON, 10)));
        NpcDialogue dialogue = new NpcDialogue("Bienvenue !", List.of());
        return new GameNpcSeller(UUID.randomUUID(), "Aubergiste", UUID.randomUUID(), "Un aubergiste jovial.", dialogue,
                shop);
    }

    private GamePlayer buyer(int gold) {
        return new GamePlayer(UUID.randomUUID(), UUID.randomUUID(), "Test", UUID.randomUUID(), Gender.MAN, Race.HUMAN,
                CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0, gold);
    }
}

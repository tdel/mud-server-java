package fr.idev.mudserver.domain.actor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerInventoryTest {

    @Test
    void trySpendGoldDeductsTheAmountWhenTheBalanceIsSufficient() {
        PlayerInventory inventory = new PlayerInventory(100);

        boolean spent = inventory.trySpendGold(40);

        assertThat(spent).isTrue();
        assertThat(inventory.getGold()).isEqualTo(60);
    }

    @Test
    void trySpendGoldLeavesTheBalanceUntouchedWhenInsufficient() {
        PlayerInventory inventory = new PlayerInventory(10);

        boolean spent = inventory.trySpendGold(40);

        assertThat(spent).isFalse();
        assertThat(inventory.getGold()).isEqualTo(10);
    }

    @Test
    void trySpendGoldSucceedsWhenTheAmountExactlyMatchesTheBalance() {
        PlayerInventory inventory = new PlayerInventory(25);

        boolean spent = inventory.trySpendGold(25);

        assertThat(spent).isTrue();
        assertThat(inventory.getGold()).isZero();
    }
}

package fr.idev.mudserver.domain.actor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionEconomyTest {

    @Test
    void defaultsToOneActionAndNoExtraActions() {
        ActionEconomy economy = new ActionEconomy();

        assertThat(economy.getActionsMax()).isEqualTo(1);
        assertThat(economy.getActionsRemaining()).isEqualTo(1);
        assertThat(economy.getExtraActionsMax()).isEqualTo(0);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(0);
    }

    @Test
    void trySpendActionSpendsTheNormalPoolBeforeTheExtraPool() {
        ActionEconomy economy = new ActionEconomy();
        economy.setExtraActionsMax(1);
        economy.resetForTurn();

        assertThat(economy.trySpendAction()).isTrue();
        assertThat(economy.getActionsRemaining()).isEqualTo(0);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(1);

        assertThat(economy.trySpendAction()).isTrue();
        assertThat(economy.getActionsRemaining()).isEqualTo(0);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(0);

        assertThat(economy.trySpendAction()).isFalse();
    }

    @Test
    void hasActionRemainingReflectsBothPools() {
        ActionEconomy economy = new ActionEconomy();
        economy.trySpendAction();

        assertThat(economy.hasActionRemaining()).isFalse();

        economy.grantBonusExtraActionsThisTurn(1);

        assertThat(economy.hasActionRemaining()).isTrue();
    }

    @Test
    void resetForTurnRestoresRemainingToMaxAndDropsTemporaryGrants() {
        ActionEconomy economy = new ActionEconomy();
        economy.grantBonusActionThisTurn();
        economy.grantBonusExtraActionsThisTurn(2);
        assertThat(economy.getActionsRemaining()).isEqualTo(2);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(2);

        economy.resetForTurn();

        assertThat(economy.getActionsRemaining()).isEqualTo(economy.getActionsMax());
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(economy.getExtraActionsMax());
    }

    @Test
    void permanentMaxOnlyTakesEffectOnTheNextReset() {
        ActionEconomy economy = new ActionEconomy();

        economy.setActionsMax(2);
        economy.setExtraActionsMax(1);
        assertThat(economy.getActionsRemaining()).as("un octroi permanent ne change pas le solde du tour en cours")
                .isEqualTo(1);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(0);

        economy.resetForTurn();

        assertThat(economy.getActionsRemaining()).isEqualTo(2);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(1);
    }

    @Test
    void temporaryGrantsTakeEffectImmediatelyAndVanishAfterTheNextReset() {
        ActionEconomy economy = new ActionEconomy();

        economy.grantBonusActionThisTurn();
        economy.grantBonusExtraActionsThisTurn(2);
        assertThat(economy.getActionsRemaining()).isEqualTo(2);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(2);

        economy.resetForTurn();

        assertThat(economy.getActionsRemaining()).isEqualTo(ActionEconomy.DEFAULT_ACTIONS_MAX);
        assertThat(economy.getExtraActionsRemaining()).isEqualTo(ActionEconomy.DEFAULT_EXTRA_ACTIONS_MAX);
    }
}

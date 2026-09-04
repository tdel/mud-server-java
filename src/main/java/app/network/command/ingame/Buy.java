package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.NpcSellerInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.NotEnoughGold;
import app.network.message.ingame.ShopItemNotFound;
import app.network.message.ingame.TargetNotFound;

// Achat en une commande stateless : "<npcUuid>|<itemTemplateUuidOuNom>|<quantité>",
// résolu et vérifié ici, la solvabilité/le débit restant entièrement dans
// NpcSellerInstance.sell (achat tout-ou-rien, cf. son commentaire).
@Component
public class Buy implements CommandHandler {

    private static final String USAGE = "buy <npcUuid>|<itemTemplateUuidOuNom>|<quantité>";

    @Override
    public String name() {
        return "buy";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String[] parts = argument.trim().split("\\|", -1);
        if (parts.length != 3) {
            connection.send(new Usage(USAGE));
            return;
        }

        String npcArg = parts[0].trim();
        String itemArg = parts[1].trim();
        Optional<NpcSellerInstance> seller = CommandArguments.tryParseUuid(npcArg)
                .flatMap(id -> character.getMotionSystem().getCurrentMap().findNpcById(id))
                .filter(NpcSellerInstance.class::isInstance).map(NpcSellerInstance.class::cast);
        if (seller.isEmpty()) {
            connection.send(new TargetNotFound(npcArg));
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            connection.send(new Usage(USAGE));
            return;
        }
        if (quantity < 1) {
            connection.send(new Usage(USAGE));
            return;
        }

        switch (seller.get().sell(character, itemArg, quantity)) {
            case NpcSellerInstance.PurchaseOutcome.Purchased ignored -> {
            }
            case NpcSellerInstance.PurchaseOutcome.EntryNotFound ignored ->
                connection.send(new ShopItemNotFound(itemArg));
            case NpcSellerInstance.PurchaseOutcome.InsufficientGold(int price) ->
                connection.send(new NotEnoughGold(price));
        }
    }
}

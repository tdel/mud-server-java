package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GameNpcSeller;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.DialogueEnded;
import fr.idev.mudserver.network.message.ingame.DialogueOptions;
import fr.idev.mudserver.network.message.ingame.InvalidDialogueChoice;
import fr.idev.mudserver.network.message.ingame.NotEnoughGold;
import fr.idev.mudserver.network.message.ingame.NpcDescription;
import fr.idev.mudserver.network.message.ingame.NpcResponse;
import fr.idev.mudserver.network.message.ingame.ShopCatalog;
import fr.idev.mudserver.network.message.ingame.ShopItemNotFound;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

/**
 * Ouvre la conversation avec un PNJ de la room courante — soit une simple ligne
 * de saveur ({@link NpcDescription}, PNJ sans {@code GameNpc.NpcDialogue}),
 * soit une boîte de dialogue bloquante. Le blocage vient uniquement de
 * {@link Connection#requestBlocking} (voir sa Javadoc dans
 * {@code TelnetConnection}), pas d'un flag sur {@code GameCharacter} : tant
 * qu'un prompt de dialogue est en attente, la ligne suivante du joueur ne
 * repasse jamais par {@code ControllerDispatcher}. Suit le même style de
 * continuation récursive que {@code controller.authed.CharacterCreate} : chaque
 * étape (salutation, boutique) se re-invoque elle-même sur choix invalide ou
 * après une réponse canée, jusqu'à ce que le joueur choisisse {@code LEAVE}, où
 * l'on cesse simplement d'appeler {@code requestBlocking}.
 *
 * <p>
 * La résolution du choix de dialogue, la résolution d'une entrée de boutique et
 * l'achat lui-même vivent désormais sur {@code GameNpc.NpcDialogue}/
 * {@link GameNpcSeller} plutôt qu'ici — ce controller ne fait qu'appeler ces
 * méthodes de domaine et brancher sur leur résultat, même style que
 * {@code Take}/{@code Equip}.
 */
@Component
public class Talk implements ControllerHandler {

    private final GameWorld gameWorld;

    public Talk(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "talk";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("talk <npc>"));
            return;
        }

        Optional<GameNpc> npc = character.getCurrentRoom().findNpcByName(name);
        if (npc.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        Optional<GameNpc.NpcDialogue> dialogue = npc.get().getDialogue();
        if (dialogue.isEmpty()) {
            connection.send(new NpcDescription(npc.get()));
            return;
        }

        promptDialogue(connection, character, npc.get(), dialogue.get());
    }

    private void promptDialogue(Connection connection, GamePlayer character, GameNpc npc,
            GameNpc.NpcDialogue dialogue) {
        connection.requestBlocking(new DialogueOptions(npc.getName(), dialogue.greeting(), dialogue.options()),
                line -> {
                    Optional<GameNpc.NpcDialogueOption> choice = dialogue.resolveOption(line);

                    if (choice.isEmpty()) {
                        connection.send(new InvalidDialogueChoice(line.trim()));
                        promptDialogue(connection, character, npc, dialogue);
                        return;
                    }

                    switch (choice.get().type()) {
                        case RESPONSE -> {
                            connection.send(new NpcResponse(npc.getName(), choice.get().response()));
                            promptDialogue(connection, character, npc, dialogue);
                        }
                        case SHOP -> {
                            if (npc instanceof GameNpcSeller seller) {
                                promptShop(connection, character, seller, dialogue);
                            }
                        }
                        case LEAVE -> connection.send(new DialogueEnded(npc.getName()));
                    }
                });
    }

    private void promptShop(Connection connection, GamePlayer character, GameNpcSeller npc,
            GameNpc.NpcDialogue dialogue) {
        List<ShopCatalog.Entry> entries = npc.shop().items().stream()
                .map(entry -> new ShopCatalog.Entry(entry.itemName(), entry.rarity(), entry.price())).toList();

        connection.requestBlocking(new ShopCatalog(npc.getName(), entries, character.getInventory().getGold()),
                line -> {
                    String trimmed = line.trim();
                    if (trimmed.equals("0") || trimmed.equalsIgnoreCase("back") || trimmed.equalsIgnoreCase("retour")) {
                        promptDialogue(connection, character, npc, dialogue);
                        return;
                    }

                    switch (npc.sell(character, trimmed)) {
                        case GameNpcSeller.PurchaseOutcome.Purchased ignored -> {
                        }
                        case GameNpcSeller.PurchaseOutcome.EntryNotFound ignored ->
                            connection.send(new ShopItemNotFound(trimmed));
                        case GameNpcSeller.PurchaseOutcome.InsufficientGold(int price) ->
                            connection.send(new NotEnoughGold(price));
                    }
                    promptShop(connection, character, npc, dialogue);
                });
    }
}

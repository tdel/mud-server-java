package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
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

@Component
public class Talk implements ControllerHandler {

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
        CharacterInstance character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("talk <npc>"));
            return;
        }

        Optional<AbstractNpc> npc = character.getCurrentRoom().findNpcByName(name);
        if (npc.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        Optional<AbstractNpc.NpcDialogue> dialogue = npc.get().getDialogue();
        if (dialogue.isEmpty()) {
            connection.send(new NpcDescription(npc.get()));
            return;
        }

        promptDialogue(connection, character, npc.get(), dialogue.get());
    }

    private void promptDialogue(Connection connection, CharacterInstance character, AbstractNpc npc,
            AbstractNpc.NpcDialogue dialogue) {
        connection.requestBlocking(new DialogueOptions(npc.getName(), dialogue.greeting(), dialogue.options()),
                line -> {
                    Optional<AbstractNpc.NpcDialogueOption> choice = dialogue.resolveOption(line);

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
                            if (npc instanceof NpcSellerInstance seller) {
                                promptShop(connection, character, seller, dialogue);
                            }
                        }
                        case LEAVE -> connection.send(new DialogueEnded(npc.getName()));
                    }
                });
    }

    private void promptShop(Connection connection, CharacterInstance character, NpcSellerInstance npc,
            AbstractNpc.NpcDialogue dialogue) {
        List<ShopCatalog.Entry> entries = npc.shop().items().stream()
                .map(entry -> new ShopCatalog.Entry(entry.itemTemplate().getName(), entry.itemTemplate().getRarity(),
                        entry.price()))
                .toList();

        connection.requestBlocking(new ShopCatalog(npc.getName(), entries, character.getInventory().getGold()),
                line -> {
                    String trimmed = line.trim();
                    if (trimmed.equals("0") || trimmed.equalsIgnoreCase("back") || trimmed.equalsIgnoreCase("retour")) {
                        promptDialogue(connection, character, npc, dialogue);
                        return;
                    }

                    switch (npc.sell(character, trimmed)) {
                        case NpcSellerInstance.PurchaseOutcome.Purchased ignored -> {
                        }
                        case NpcSellerInstance.PurchaseOutcome.EntryNotFound ignored ->
                            connection.send(new ShopItemNotFound(trimmed));
                        case NpcSellerInstance.PurchaseOutcome.InsufficientGold(int price) ->
                            connection.send(new NotEnoughGold(price));
                    }
                    promptShop(connection, character, npc, dialogue);
                });
    }
}

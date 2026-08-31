package app.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.NpcSellerInstance;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.DialogueEnded;
import app.network.message.ingame.DialogueOptions;
import app.network.message.ingame.InvalidDialogueChoice;
import app.network.message.ingame.NotEnoughGold;
import app.network.message.ingame.NpcDescription;
import app.network.message.ingame.NpcResponse;
import app.network.message.ingame.ShopCatalog;
import app.network.message.ingame.ShopItemNotFound;
import app.network.message.ingame.TargetNotFound;

@Component
public class Talk implements CommandHandler {

    @Override
    public String name() {
        return "talk";
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
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("talk <uuid>"));
            return;
        }

        Optional<AbstractNpc> npc = CommandArguments.tryParseUuid(raw)
                .flatMap(id -> character.getCurrentMap().findNpcById(id));
        if (npc.isEmpty()) {
            connection.send(new TargetNotFound(raw));
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
        connection.requestBlocking(
                new DialogueOptions(npc.getId(), npc.getName(), dialogue.greeting(), dialogue.options()), line -> {
                    Optional<AbstractNpc.NpcDialogueOption> choice = dialogue.resolveOption(line);

                    if (choice.isEmpty()) {
                        connection.send(new InvalidDialogueChoice(line.trim()));
                        promptDialogue(connection, character, npc, dialogue);
                        return;
                    }

                    switch (choice.get().type()) {
                        case RESPONSE -> {
                            connection.send(new NpcResponse(npc.getId(), npc.getName(), choice.get().response()));
                            promptDialogue(connection, character, npc, dialogue);
                        }
                        case SHOP -> {
                            if (npc instanceof NpcSellerInstance seller) {
                                promptShop(connection, character, seller, dialogue);
                            }
                        }
                        case LEAVE -> connection.send(new DialogueEnded(npc.getId(), npc.getName()));
                    }
                });
    }

    private void promptShop(Connection connection, CharacterInstance character, NpcSellerInstance npc,
            AbstractNpc.NpcDialogue dialogue) {
        List<ShopCatalog.Entry> entries = npc.shop().items().stream()
                .map(entry -> new ShopCatalog.Entry(entry.itemTemplate().getId(), entry.itemTemplate().getName(),
                        entry.itemTemplate().getGrade(), entry.price()))
                .toList();

        connection.requestBlocking(
                new ShopCatalog(npc.getId(), npc.getName(), entries, character.getInventorySystem().getGold()),
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

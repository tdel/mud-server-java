package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.Client;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotFound;
import fr.idev.mudserver.network.message.ingame.ItemTaken;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Take implements ControllerHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;
    private final GameWorld gameWorld;

    public Take(ItemService itemService, ItemTemplateDao itemTemplateDao, GameWorld gameWorld) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "take";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        Client client = gameWorld.client(session);
        String name = argument.trim();

        if (name.isEmpty()) {
            client.send(new Usage("take <name>"));
            return;
        }

        Character character = client.character();
        Optional<Item> item = itemService.findItemInRoomByName(character.getCurrentRoomId(), name);

        if (item.isEmpty()) {
            client.send(new ItemNotFound(name));
            return;
        }

        if (!itemService.addItemToInventory(item.get(), character)) {
            // Quelqu'un d'autre l'a pris entre-temps — du point de vue de ce joueur,
            // indiscernable du fait qu'il n'était jamais là.
            client.send(new ItemNotFound(name));
            return;
        }

        String templateName = itemTemplateDao.findById(item.get().getTemplateId()).map(ItemTemplate::getName)
                .orElseThrow();
        client.send(new ItemTaken(templateName));
    }
}

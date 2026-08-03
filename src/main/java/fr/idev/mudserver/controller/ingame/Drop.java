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
import fr.idev.mudserver.network.message.ingame.ItemDropped;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Drop implements ControllerHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;
    private final GameWorld gameWorld;

    public Drop(ItemService itemService, ItemTemplateDao itemTemplateDao, GameWorld gameWorld) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "drop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Client client = gameWorld.client(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            client.send(new Usage("drop <name>"));
            return;
        }

        Character character = client.character();
        Optional<Item> item = itemService.findItemByName(character, name);

        if (item.isEmpty()) {
            client.send(new ItemNotCarried(name));
            return;
        }

        itemService.removeItemFromInventory(item.get(), character);

        String templateName = itemTemplateDao.findById(item.get().getTemplateId()).map(ItemTemplate::getName)
                .orElseThrow();
        client.send(new ItemDropped(templateName));
    }
}

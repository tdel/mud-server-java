package fr.idev.mudserver.controller.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Inventory implements ControllerHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;
    private final GameWorld gameWorld;

    public Inventory(ItemService itemService, ItemTemplateDao itemTemplateDao, GameWorld gameWorld) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        PlayerInstance player = gameWorld.player(session);

        List<Item> items = itemService.getInventory(player.character());
        List<String> names = items.stream()
                .map(item -> itemTemplateDao.findById(item.templateId()).map(ItemTemplate::name).orElseThrow())
                .toList();

        player.send(new fr.idev.mudserver.network.message.ingame.Inventory(names));
    }
}

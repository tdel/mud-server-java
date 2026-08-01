package fr.idev.mudserver.network.action.ingame;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Inventory implements ActionHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;

    public Inventory(ItemService itemService, ItemTemplateDao itemTemplateDao) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
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
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();

        List<Item> items = itemService.getInventory(player.character());
        List<String> names = items.stream()
                .map(item -> itemTemplateDao.findById(item.templateId()).map(ItemTemplate::name).orElseThrow())
                .toList();

        player.send(new fr.idev.mudserver.network.message.ingame.Inventory(names));
    }
}

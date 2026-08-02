package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.PlayerInstance;
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

    public Drop(ItemService itemService, ItemTemplateDao itemTemplateDao) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
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
    public void onReceive(Connection session, String argument) {
        PlayerInstance player = session.player();
        String name = argument.trim();

        if (name.isEmpty()) {
            player.send(new Usage("drop <name>"));
            return;
        }

        Character character = player.character();
        Optional<Item> item = itemService.findItemByName(character, name);

        if (item.isEmpty()) {
            player.send(new ItemNotCarried(name));
            return;
        }

        itemService.removeItemFromInventory(item.get(), character);

        String templateName = itemTemplateDao.findById(item.get().templateId()).map(ItemTemplate::name).orElseThrow();
        player.send(new ItemDropped(templateName));
    }
}

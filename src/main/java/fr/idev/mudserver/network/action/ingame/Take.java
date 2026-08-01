package fr.idev.mudserver.network.action.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotFound;
import fr.idev.mudserver.network.message.ingame.ItemTaken;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Take implements ActionHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;

    public Take(ItemService itemService, ItemTemplateDao itemTemplateDao) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
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
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();
        String name = argument.trim();

        if (name.isEmpty()) {
            player.send(new Usage("take <name>"));
            return;
        }

        Character character = player.character();
        Optional<Item> item = itemService.findItemInRoomByName(character.currentRoomId(), name);

        if (item.isEmpty()) {
            player.send(new ItemNotFound(name));
            return;
        }

        if (!itemService.addItemToInventory(item.get(), character)) {
            // Quelqu'un d'autre l'a pris entre-temps — du point de vue de ce joueur,
            // indiscernable du fait qu'il n'était jamais là.
            player.send(new ItemNotFound(name));
            return;
        }

        String templateName = itemTemplateDao.findById(item.get().templateId()).map(ItemTemplate::name).orElseThrow();
        player.send(new ItemTaken(templateName));
    }
}

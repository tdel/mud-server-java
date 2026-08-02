package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemEquipped;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemNotEquippable;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class Equip implements ControllerHandler {

    private final ItemService itemService;
    private final ItemTemplateDao itemTemplateDao;

    public Equip(ItemService itemService, ItemTemplateDao itemTemplateDao) {
        this.itemService = itemService;
        this.itemTemplateDao = itemTemplateDao;
    }

    @Override
    public String name() {
        return "equip";
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
            player.send(new Usage("equip <name>"));
            return;
        }

        Character character = player.character();
        Optional<Item> item = itemService.findItemByName(character, name);

        if (item.isEmpty()) {
            player.send(new ItemNotCarried(name));
            return;
        }

        String templateName = itemTemplateDao.findById(item.get().templateId()).map(ItemTemplate::name).orElseThrow();
        Optional<EquipmentSlot> slot = itemService.equipItem(item.get(), character);

        if (slot.isEmpty()) {
            player.send(new ItemNotEquippable(templateName));
            return;
        }

        player.send(new ItemEquipped(templateName, slot.get()));
    }
}

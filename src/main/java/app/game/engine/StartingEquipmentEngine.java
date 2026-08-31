package app.game.engine;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.game.catalog.ItemTemplateCatalog;

@Component
public class StartingEquipmentEngine {

    private static final Logger log = LoggerFactory.getLogger(StartingEquipmentEngine.class);

    private static final UUID FIGHTER_WEAPON_TEMPLATE_ID = UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df");
    private static final UUID MYSTIC_WEAPON_TEMPLATE_ID = UUID.fromString("ac9fe757-ab75-47ef-b413-8844d5c5ed73");

    private final ItemTemplateCatalog itemTemplateCatalog;

    public StartingEquipmentEngine(ItemTemplateCatalog itemTemplateCatalog) {
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        CharacterInstance character = event.character();
        UUID templateId = switch (character.getCharacterClass()) {
            case FIGHTER -> FIGHTER_WEAPON_TEMPLATE_ID;
            case MYSTIC -> MYSTIC_WEAPON_TEMPLATE_ID;
        };

        ItemTemplate template = itemTemplateCatalog.getById(templateId);
        Item weapon = new Item(UUID.randomUUID(), template, character, null);
        character.getInventorySystem().receiveLootItem(weapon);
        character.getInventorySystem().equipItem(weapon);

        log.info("character.starting_equipment_granted character={} item={}", character.getName(), weapon.getName());
    }
}

package app.game.engine;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.actor.event.NewGamePlayerCreated;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.game.catalog.ItemTemplateCatalog;

@Component
public class StartingEquipmentEngine {

    private static final Logger log = LoggerFactory.getLogger(StartingEquipmentEngine.class);

    // Wooden Sword / Wooden Staff : les seules armes NOGRADE du catalogue (pas de
    // <expect> dans weapons.xml) — un Short Sword/Basic Wizard Staff (grade D)
    // déclencherait immédiatement le malus "Grade Penalty" (Expertise Grade
    // niveau 1 ne s'apprend qu'au niveau 20, cf. fighter.xml/mystic.xml), ce qui
    // punirait un personnage fraîchement créé.
    private static final UUID FIGHTER_WEAPON_TEMPLATE_ID = UUID.fromString("558543a4-39d4-4f36-ac1e-2881deac24a6");
    private static final UUID MYSTIC_WEAPON_TEMPLATE_ID = UUID.fromString("7b268503-d3f0-43eb-9144-bbb008d6ddeb");
    // Apprentice's Robe (ROBE, NOGRADE) : comme dans L2, un mystique débute en
    // robe — sans elle, Spellcraft/Magician's Movement le pénaliseraient d'emblée.
    private static final UUID MYSTIC_ROBE_TEMPLATE_ID = UUID.fromString("ab5cce0e-1d3f-49f8-9d6f-d814321a566f");

    private final ItemTemplateCatalog itemTemplateCatalog;

    public StartingEquipmentEngine(ItemTemplateCatalog itemTemplateCatalog) {
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        PlayerInstance character = event.character();
        List<UUID> templateIds = switch (character.getClassSystem().getCharacterClass()) {
            case FIGHTER -> List.of(FIGHTER_WEAPON_TEMPLATE_ID);
            case MYSTIC -> List.of(MYSTIC_WEAPON_TEMPLATE_ID, MYSTIC_ROBE_TEMPLATE_ID);
        };

        for (UUID templateId : templateIds) {
            ItemTemplate template = itemTemplateCatalog.getById(templateId);
            Item item = new Item(UUID.randomUUID(), template, character, null);
            character.getInventorySystem().receiveLootItem(item);
            character.getInventorySystem().equipItem(item);
            log.info("character.starting_equipment_granted character={} item={}", character.getName(), item.getName());
        }
    }
}

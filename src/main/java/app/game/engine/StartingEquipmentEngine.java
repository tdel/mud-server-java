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

    // Wooden Sword / Wooden Staff : les seules armes NOGRADE du catalogue (pas de
    // <expect> dans weapons.xml) — un Short Sword/Basic Wizard Staff (grade D)
    // déclencherait immédiatement le malus "Grade Penalty" (Expertise Grade
    // niveau 1 ne s'apprend qu'au niveau 20, cf. fighter.xml/mystic.xml), ce qui
    // punirait un personnage fraîchement créé.
    private static final UUID FIGHTER_WEAPON_TEMPLATE_ID = UUID.fromString("558543a4-39d4-4f36-ac1e-2881deac24a6");
    private static final UUID MYSTIC_WEAPON_TEMPLATE_ID = UUID.fromString("7b268503-d3f0-43eb-9144-bbb008d6ddeb");

    private final ItemTemplateCatalog itemTemplateCatalog;

    public StartingEquipmentEngine(ItemTemplateCatalog itemTemplateCatalog) {
        this.itemTemplateCatalog = itemTemplateCatalog;
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        CharacterInstance character = event.character();
        UUID templateId = switch (character.getClassSystem().getCharacterClass()) {
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

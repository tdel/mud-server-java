package fr.idev.mudserver.game.actor;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.game.RoomService;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonsterServiceTest {

    private static final UUID VILLAGE_SQUARE_ID = UUID.fromString("5e4ada37-37e1-438c-9233-581f10c055c7");
    private static final UUID FOREST_EDGE_ID = UUID.fromString("9a884ac7-b954-4cd6-ab67-c677d472cb0f");
    private static final UUID TAVERN_ID = UUID.fromString("e1da77bd-f0b3-4d5a-95da-e8765c4fc973");
    private static final UUID CLEARING_ID = UUID.fromString("7f55fd0c-23f8-4a3b-82a2-95a79bdbf2b5");
    private static final UUID CEMETERY_PATH_ID = UUID.fromString("4dae9974-45f7-46c9-8e66-12cdac759860");

    /**
     * Tirée d'une {@code ItemService} jetable (pas de DAO nécessaire, {@code
     * warmItemTemplates}/{@code templateIds} ne touchent jamais la DB) plutôt que
     * recopiée à la main, pour rester synchronisée avec {@code data/items.json}
     * sans dépendre d'un contexte Spring dans ce test unitaire pur.
     */
    private static Set<UUID> realItemTemplateIds() {
        ItemService itemService = new ItemService(null, new ObjectMapper(), new DiceRoller());
        itemService.warmItemTemplates();
        return itemService.templateIds();
    }

    /**
     * Même principe que {@link #realItemTemplateIds()} : les spawns vivent
     * maintenant dans {@code data/rooms.json}, donc obtenir des {@code Room} avec
     * leurs vrais {@code monsterSpawns} passe par un {@code RoomService} jetable
     * plutôt que par des {@code Room} construites à la main. Pas de
     * {@code CharacterDao} nécessaire tant qu'on ne touche que
     * {@code warmRooms()}/{@code allRooms()}.
     */
    private static Collection<Room> realRooms() {
        RoomService roomService = new RoomService(new ObjectMapper(), null);
        roomService.warmRooms();
        return roomService.allRooms();
    }

    @Test
    void warmMonstersLoadsTheRealCatalogFromJsonAndPlacesItInItsRoom() {
        MonsterService monsterService = new MonsterService(new ObjectMapper());
        Collection<Room> rooms = realRooms();

        monsterService.warmMonsters(rooms, realItemTemplateIds());

        Room villageSquare = room(rooms, VILLAGE_SQUARE_ID);
        Room forestEdge = room(rooms, FOREST_EDGE_ID);
        Room tavern = room(rooms, TAVERN_ID);
        Room clearing = room(rooms, CLEARING_ID);
        Room cemeteryPath = room(rooms, CEMETERY_PATH_ID);

        assertThat(villageSquare.getMonsters()).hasSize(0);
        assertThat(forestEdge.getMonsters()).hasSize(2);
        assertThat(tavern.getMonsters()).hasSize(1);
        assertThat(clearing.getMonsters()).hasSize(3);
        assertThat(cemeteryPath.getMonsters()).hasSize(1);
        GameMonster goblin = clearing.getMonsters().get(0);
        assertThat(goblin.getName()).isEqualTo("Gobelin");
        assertThat(goblin.getMaxHealth()).isEqualTo(7);
        assertThat(goblin.getCurrentHealth()).isEqualTo(7);
        assertThat(goblin.getAttribute(Attribute.DEXTERITY)).isEqualTo(14);
        assertThat(goblin.getArmorClass()).isEqualTo(15);
        assertThat(goblin.getCurrentRoom()).isEqualTo(clearing);
        assertThat(goblin.getDescription()).isNotBlank();
        assertThat(goblin.getTemplate().getXpReward()).isEqualTo(50);
        assertThat(goblin.getTemplate().getGoldReward()).isEqualTo(5);
        assertThat(goblin.getTemplate().getLootTable()).isNotEmpty();
        assertThat(goblin.getPresenceRadius()).isEqualTo(2);
    }

    @Test
    void loadMonstersThrowsWhenASpawnReferencesAnUnknownTemplate() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        Room room = new Room(UUID.randomUUID(), "Test", "...", null);
        room.setMonsterSpawns(List.of(new MonsterSpawn(UUID.randomUUID(), UUID.randomUUID(), new HexCoordinate(0, 0))));

        assertThatThrownBy(() -> isolated.loadMonsters(List.of(), List.of(room), Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadMonstersThrowsWhenALootTableEntryReferencesAnUnknownItemTemplate() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        UUID templateId = UUID.randomUUID();
        MonsterService.MonsterTemplateDefinition template = new MonsterService.MonsterTemplateDefinition(templateId,
                "Test", "...", 5, TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, "1d4", 0,
                List.of(new LootTableEntry(UUID.randomUUID(), 0.1)), 0);

        assertThatThrownBy(() -> isolated.loadMonsters(List.of(template), List.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadMonstersThrowsWhenALootTableEntryHasADropChanceOutOfRange() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        UUID templateId = UUID.randomUUID();
        UUID itemTemplateId = UUID.randomUUID();
        MonsterService.MonsterTemplateDefinition template = new MonsterService.MonsterTemplateDefinition(templateId,
                "Test", "...", 5, TestAttributes.of(10, 10, 10, 10, 10, 10), null, 0, "1d4", 0,
                List.of(new LootTableEntry(itemTemplateId, 1.5)), 0);

        assertThatThrownBy(() -> isolated.loadMonsters(List.of(template), List.of(), Set.of(itemTemplateId)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Room room(Collection<Room> rooms, UUID roomId) {
        return rooms.stream().filter(room -> room.getId().equals(roomId)).findFirst().orElseThrow();
    }
}

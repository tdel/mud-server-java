package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.domain.actor.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.TestAttributes;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le placement bout-en-bout depuis le vrai catalogue
 * ({@code data/monsters.json} + {@code data/worlds/default/rooms.json}) est
 * couvert par
 * {@code RoomServiceTest#warmRoomsPlacesRealMonstersFromJsonInTheirRooms} —
 * {@code WorldInstanceService.materialize} charge désormais aussi les items au
 * sol depuis la DB (voir sa Javadoc), donc obtenir un graphe de
 * {@link RoomInstance} réel demande un contexte Spring/DB, plus possible dans
 * ce test JUnit pur. Cette classe ne garde que les cas d'erreur de
 * {@link MonsterService#loadMonsters}, indépendants de tout graphe de rooms.
 */
class MonsterServiceTest {

    @Test
    void loadMonstersThrowsWhenASpawnReferencesAnUnknownTemplate() {
        MonsterService isolated = new MonsterService(new ObjectMapper());
        RoomInstance room = new RoomInstance(UUID.randomUUID(), "Test", "...", null);
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
}

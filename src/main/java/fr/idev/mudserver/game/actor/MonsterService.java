package fr.idev.mudserver.game.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.MonsterSpawn;
import fr.idev.mudserver.domain.RoomInstance;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Précharge les templates de monstres depuis {@code data/monsters.json}, sur le
 * même principe que {@code ItemService}/{@code RoomService} : donnée de contenu
 * statique, jamais mutée en jeu, chargée depuis le classpath plutôt que la DB —
 * pas de table monstre dans {@code V1__init_schema.sql}. Les instances («
 * spawns ») ne vivent plus dans ce fichier : elles sont portées par chaque
 * {@code RoomInstance} (voir {@link RoomInstance#getMonsterSpawns()}, peuplé
 * par {@code RoomService.loadRooms} depuis {@code data/rooms.json}) — c'est
 * {@link #loadMonsters} qui les consomme pour instancier et placer les
 * {@link GameMonster} correspondants, une fois les templates chargés.
 */
@Service
public class MonsterService {

    private static final Logger log = LoggerFactory.getLogger(MonsterService.class);

    private static final String MONSTERS_RESOURCE = "/data/monsters.json";

    private final Map<UUID, MonsterTemplate> templates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public MonsterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmMonsterTemplates(Set<UUID> knownItemTemplateIds) {
        try (InputStream in = getClass().getResourceAsStream(MONSTERS_RESOURCE)) {
            List<MonsterTemplateDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<MonsterTemplateDefinition>>() {
                    });
            registerTemplates(definitions, knownItemTemplateIds);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + MONSTERS_RESOURCE, e);
        }
    }

    /**
     * Placement runtime, appelé par {@code WorldInstanceService.materialize} une
     * fois par {@code WorldInstance} — {@link #warmMonsterTemplates} doit déjà
     * avoir tourné (une fois pour tout le process, les templates sont globaux).
     */
    public void placeMonsters(Collection<RoomInstance> rooms) {
        int placedCount = 0;
        for (RoomInstance room : rooms) {
            for (MonsterSpawn spawn : room.getMonsterSpawns()) {
                MonsterTemplate template = templates.get(spawn.templateId());
                if (template == null) {
                    throw new IllegalStateException("Spawn " + spawn.id() + " de la room " + room.getId()
                            + " référence le template " + spawn.templateId() + ", absent de " + MONSTERS_RESOURCE);
                }

                GameMonster monster = new GameMonster(spawn.id(), template.getName(), template.getId(), room.getId(),
                        template.getAttributes(), template.getMaxHealth());
                monster.attachTemplate(template);
                monster.setCurrentRoom(room);
                room.placeMonster(monster, spawn.cell());
                placedCount++;
            }
        }

        log.info("monster.instances_placed count={}", placedCount);
    }

    void registerTemplates(List<MonsterTemplateDefinition> definitions, Set<UUID> knownItemTemplateIds) {
        for (MonsterTemplateDefinition definition : definitions) {
            for (LootTableEntry entry : definition.lootTable()) {
                if (entry.dropChance() < 0 || entry.dropChance() > 1) {
                    throw new IllegalStateException("Template " + definition.id() + " a une entrée de lootTable avec "
                            + "dropChance=" + entry.dropChance() + " hors de [0, 1] dans " + MONSTERS_RESOURCE);
                }
                if (!knownItemTemplateIds.contains(entry.itemTemplateId())) {
                    throw new IllegalStateException("Template " + definition.id() + " référence l'item "
                            + entry.itemTemplateId() + " dans sa lootTable, absent de data/items.json");
                }
            }
            templates.put(definition.id(),
                    new MonsterTemplate(definition.id(), definition.name(), definition.description(),
                            definition.maxHealth(), definition.attributes(), definition.naturalArmorClass(),
                            definition.xpReward(), definition.naturalDamageDice(), definition.goldReward(),
                            definition.lootTable(), definition.presenceRadius()));
        }
        log.info("monster.templates_loaded count={}", templates.size());
    }

    /**
     * Combinateur conservé pour les tests en boîte blanche
     * ({@code MonsterServiceTest}) qui veulent enregistrer des templates et placer
     * des instances en un seul appel, sans passer par le classpath.
     */
    void loadMonsters(List<MonsterTemplateDefinition> definitions, Collection<RoomInstance> rooms,
            Set<UUID> knownItemTemplateIds) {
        registerTemplates(definitions, knownItemTemplateIds);
        placeMonsters(rooms);
    }

    record MonsterTemplateDefinition(UUID id, String name, String description, int maxHealth,
            Map<Attribute, Integer> attributes, Integer naturalArmorClass, int xpReward, String naturalDamageDice,
            int goldReward, List<LootTableEntry> lootTable, int presenceRadius) {
    }
}

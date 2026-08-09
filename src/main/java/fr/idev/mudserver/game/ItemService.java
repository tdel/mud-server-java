package fr.idev.mudserver.game;

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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.domain.ArmorCategory;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.domain.Rarity;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemPickedUp;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.network.message.ingame.EquipmentLooted;
import fr.idev.mudserver.network.message.ingame.ItemBought;
import fr.idev.mudserver.persistence.ItemDao;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache de lecture pour les items d'un personnage — le sac comme les
 * emplacements équipés — et ceux posés au sol dans une room, et point de
 * persistance réactif pour leurs mutations : ramassage, dépôt, équipement et
 * déséquipement vivent tous désormais sur {@code GamePlayer}
 * ({@link GamePlayer#pickUpItem}/{@link GamePlayer#dropItem}/
 * {@link GamePlayer#equipItem}/{@link GamePlayer#unequipItem}), qui publie un
 * événement de domaine après chaque mutation en mémoire ; les méthodes
 * {@code @EventListener} de cette classe répercutent chacune en base via
 * {@link ItemDao}. Précharge aussi l'ensemble des {@link ItemTemplate} en
 * mémoire ({@link #warmItemTemplates()}) depuis {@code data/items.json}, sur le
 * même principe que {@code RaceService.warmRaceBonuses()} : les templates sont
 * une donnée de règles statique, jamais mutée en jeu, donc jamais persistée en
 * DB. Attache le template correspondant à chaque {@code Item} avant de le
 * renvoyer à l'appelant — un {@code Item} sorti d'ici a donc toujours son
 * template, contrairement à un {@code Item} lu directement via {@link ItemDao}.
 */
@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private static final String ITEM_TEMPLATE_RESOURCE = "/data/items.json";

    private final Map<UUID, ItemTemplate> templates = new ConcurrentHashMap<>();

    private final ItemDao itemDao;
    private final ObjectMapper objectMapper;

    public ItemService(ItemDao itemDao, ObjectMapper objectMapper) {
        this.itemDao = itemDao;
        this.objectMapper = objectMapper;
    }

    public void warmItemTemplates() {
        try (InputStream in = getClass().getResourceAsStream(ITEM_TEMPLATE_RESOURCE)) {
            List<ItemTemplateDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<ItemTemplateDefinition>>() {
                    });
            for (ItemTemplateDefinition definition : definitions) {
                registerTemplate(new ItemTemplate(definition.id(), definition.name(), definition.description(),
                        definition.type(), definition.weight(), definition.armorCategory(), definition.baseAc(),
                        definition.damageDice(), definition.price(), definition.rarity(), definition.bonus()));
            }
            log.info("item.templates_loaded count={}", templates.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + ITEM_TEMPLATE_RESOURCE, e);
        }
    }

    void registerTemplate(ItemTemplate template) {
        templates.put(template.getId(), template);
    }

    /**
     * Exposé pour {@code MonsterService.loadMonsters}, qui valide au démarrage que
     * chaque {@code itemTemplateId} référencé par une table de butin de
     * {@code data/monsters.json} existe réellement — un simple {@code Set<UUID>}
     * plutôt qu'une dépendance directe à {@code ItemService} pour ne pas forcer
     * {@code MonsterServiceTest} (test JUnit pur, sans contexte Spring/DB) à
     * dépendre d'un {@code ItemDao} réel.
     */
    public Set<UUID> templateIds() {
        return Set.copyOf(templates.keySet());
    }

    /**
     * Utilisé par {@code NpcService.warmNpcs} pour dénormaliser le nom d'article et
     * sa rareté sur chaque {@code GameNpcSeller.NpcShopEntry} au chargement, sur le
     * même principe que {@link #templateIds()} — juste assez de donnée pour ne pas
     * forcer une vraie dépendance à {@code ItemService}/{@code ItemDao}.
     */
    public Map<UUID, ItemSummary> templateSummariesById() {
        Map<UUID, ItemSummary> summaries = new ConcurrentHashMap<>();
        templates.forEach(
                (id, template) -> summaries.put(id, new ItemSummary(template.getName(), template.getRarity())));
        return summaries;
    }

    public record ItemSummary(String name, Rarity rarity) {
    }

    public List<Item> loadInventory(GamePlayer character) {
        List<Item> items = attachTemplates(itemDao.findByCharacterId(character.getId()));
        items.forEach(item -> item.attachCharacter(character));
        return items;
    }

    /**
     * Précharge les items au sol de chaque room, une fois pour toute la durée du
     * process — appelé depuis {@code ServerApplication.warmupRunner} juste après
     * {@code RoomService.warmRooms()} et {@link #warmItemTemplates()}, sur le même
     * principe : une {@code Room} n'est jamais rechargée par session, contrairement
     * à un {@code GamePlayer}.
     */
    public void warmRoomItems(Collection<Room> rooms) {
        int totalItems = 0;
        for (Room room : rooms) {
            List<Item> items = attachTemplates(itemDao.findByRoomId(room.getId()));
            items.forEach(item -> item.attachRoom(room));
            room.setItems(items);
            totalItems += items.size();
        }
        log.info("item.room_items_loaded count={} rooms={}", totalItems, rooms.size());
    }

    Item attachTemplate(Item item) {
        ItemTemplate template = templates.get(item.getTemplateId());
        if (template == null) {
            throw new IllegalStateException("ItemTemplate " + item.getTemplateId()
                    + " absent du cache — warmItemTemplates() a-t-il été appelé ?");
        }
        item.attachTemplate(template);
        return item;
    }

    private List<Item> attachTemplates(List<Item> items) {
        items.forEach(this::attachTemplate);
        return items;
    }

    @EventListener
    void onItemPickedUp(ItemPickedUp event) {
        itemDao.assignToCharacter(event.item().getId(), event.character().getId());
        log.info("item.picked_up item={} template={} character={}", event.item().getId(), event.item().getTemplateId(),
                event.character().getName());
    }

    @EventListener
    void onGamePlayerDroppedItem(GamePlayerDroppedItem event) {
        itemDao.assignToRoom(event.item().getId(), event.room().getId());
        log.info("item.dropped item={} template={} room={}", event.item().getId(), event.item().getTemplateId(),
                event.room().getName());
    }

    /**
     * {@code @Transactional} pour de vrai ici : les deux {@code updateSlot}
     * (déséquiper l'ancien occupant du slot, équiper le nouveau) doivent partager
     * une transaction pour que la contrainte différée
     * {@code uniq_character_slot DEFERRABLE INITIALLY DEFERRED} (voir
     * V1__init_schema.sql) protège réellement le chevauchement transitoire entre
     * les deux UPDATE — sans transaction commune, chaque UPDATE valide
     * immédiatement en autocommit et la déférence de la contrainte ne sert à rien.
     */
    @EventListener
    @Transactional
    void onGamePlayerEquippedItem(GamePlayerEquippedItem event) {
        for (Item previousOccupant : event.previousOccupants()) {
            itemDao.updateSlot(previousOccupant.getId(), null);
        }
        itemDao.updateSlot(event.item().getId(), event.slot());
        log.info("item.equipped item={} slot={} character={} previousOccupants={}", event.item().getName(),
                event.slot(), event.character().getName(), event.previousOccupants().size());
    }

    @EventListener
    void onGamePlayerUnequippedItem(GamePlayerUnequippedItem event) {
        itemDao.updateSlot(event.item().getId(), null);
        log.info("item.unequipped item={} character={}", event.item().getName(), event.character().getName());
    }

    /**
     * Contrairement à {@link #onItemPickedUp} (simple réassignation d'une ligne
     * déjà existante), l'item issu d'un butin n'a encore aucune ligne en base —
     * {@code insert} plutôt qu'un {@code update}. Le template doit être attaché
     * avant d'envoyer le message : {@code getName()} en dépend.
     */
    @EventListener
    void onCharacterLootedItem(CharacterLootedItem event) {
        attachTemplate(event.item());
        itemDao.insert(event.item());
        event.character().send(new EquipmentLooted(event.item().getName(), event.item().getRarity()));
        log.info("item.looted item={} character={}", event.item().getName(), event.character().getName());
    }

    /**
     * Contrairement à {@link #onItemPickedUp}, l'item acheté n'a encore aucune
     * ligne en base — {@code insert}, même raisonnement que
     * {@link #onCharacterLootedItem}. Le template doit être attaché avant d'envoyer
     * le message : {@code getName()} en dépend.
     */
    @EventListener
    void onItemPurchased(ItemPurchased event) {
        attachTemplate(event.item());
        itemDao.insert(event.item());
        event.character().send(new ItemBought(event.item().getName(), event.item().getRarity(), event.price()));
        log.info("item.purchased item={} character={} price={}", event.item().getName(), event.character().getName(),
                event.price());
    }

    private record ItemTemplateDefinition(UUID id, String name, String description, ItemType type, int weight,
            ArmorCategory armorCategory, int baseAc, String damageDice, int price, Rarity rarity, int bonus) {
    }
}

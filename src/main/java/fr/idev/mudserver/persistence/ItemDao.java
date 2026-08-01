package fr.idev.mudserver.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.persistence.mapper.ItemRowMapper;

@Repository
public class ItemDao {

    private static final ItemRowMapper MAPPER = new ItemRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ItemDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Item item) {
        jdbcTemplate.update("""
                INSERT INTO item (id, template_id, room_id, character_id, slot)
                VALUES (:id, :templateId, :roomId, :characterId, :slot)
                """,
                new MapSqlParameterSource().addValue("id", item.id()).addValue("templateId", item.templateId())
                        .addValue("roomId", item.roomId()).addValue("characterId", item.characterId())
                        .addValue("slot", item.slot() == null ? null : item.slot().name()));
    }

    public Optional<Item> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM item WHERE id = :id", Map.of("id", id), MAPPER).stream().findFirst();
    }

    /**
     * Verrou pessimiste — équivalent du {@code LockMode::PESSIMISTIC_WRITE} PHP. À
     * n'appeler que dans une transaction (méthode appelante {@code @Transactional})
     * : bloque toute autre transaction voulant lire/modifier cette ligne jusqu'au
     * commit/rollback.
     */
    public Optional<Item> findByIdForUpdate(UUID id) {
        return jdbcTemplate.query("SELECT * FROM item WHERE id = :id FOR UPDATE", Map.of("id", id), MAPPER).stream()
                .findFirst();
    }

    public List<Item> findByRoomId(UUID roomId) {
        return jdbcTemplate.query("SELECT * FROM item WHERE room_id = :roomId", Map.of("roomId", roomId), MAPPER);
    }

    public List<Item> findByCharacterId(UUID characterId) {
        return jdbcTemplate.query("SELECT * FROM item WHERE character_id = :characterId",
                Map.of("characterId", characterId), MAPPER);
    }

    public void assignToCharacter(UUID itemId, UUID characterId) {
        jdbcTemplate.update(
                "UPDATE item SET character_id = :characterId, room_id = NULL, slot = NULL WHERE id = :itemId",
                Map.of("itemId", itemId, "characterId", characterId));
    }

    public void assignToRoom(UUID itemId, UUID roomId) {
        jdbcTemplate.update("UPDATE item SET room_id = :roomId, character_id = NULL, slot = NULL WHERE id = :itemId",
                Map.of("itemId", itemId, "roomId", roomId));
    }

    public void updateSlot(UUID itemId, EquipmentSlot slot) {
        jdbcTemplate.update("UPDATE item SET slot = :slot WHERE id = :itemId", new MapSqlParameterSource()
                .addValue("itemId", itemId).addValue("slot", slot == null ? null : slot.name()));
    }
}

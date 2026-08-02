package fr.idev.mudserver.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.mapper.RoomRowMapper;

@Repository
public class RoomDao {

    private static final RoomRowMapper MAPPER = new RoomRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RoomDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Room room) {
        jdbcTemplate.update("""
                INSERT INTO room (id, name, description, is_starting_room)
                VALUES (:id, :name, :description, :isStartingRoom)
                """, new MapSqlParameterSource().addValue("id", room.getId()).addValue("name", room.getName())
                .addValue("description", room.getDescription()).addValue("isStartingRoom", room.isStartingRoom()));
    }

    public Optional<Room> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM room WHERE id = :id", Map.of("id", id), MAPPER).stream().findFirst();
    }

    public Optional<Room> findByName(String name) {
        return jdbcTemplate.query("SELECT * FROM room WHERE name = :name", Map.of("name", name), MAPPER).stream()
                .findFirst();
    }

    public Optional<Room> findStartingRoom() {
        return jdbcTemplate.query("SELECT * FROM room WHERE is_starting_room = TRUE", MAPPER).stream().findFirst();
    }

    public List<Room> findAll() {
        return jdbcTemplate.query("SELECT * FROM room", MAPPER);
    }

    /**
     * Ne marque rien de plus ; l'appelant doit avoir appelé
     * {@link #clearStartingRoom()} avant si besoin.
     */
    public void markAsStartingRoom(UUID roomId) {
        jdbcTemplate.update("UPDATE room SET is_starting_room = TRUE WHERE id = :id", Map.of("id", roomId));
    }

    public void clearStartingRoom() {
        jdbcTemplate.update("UPDATE room SET is_starting_room = NULL WHERE is_starting_room = TRUE", Map.of());
    }
}

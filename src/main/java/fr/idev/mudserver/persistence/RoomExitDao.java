package fr.idev.mudserver.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.persistence.mapper.RoomExitRowMapper;

@Repository
public class RoomExitDao {

    private static final RoomExitRowMapper MAPPER = new RoomExitRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RoomExitDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(RoomExit exit) {
        jdbcTemplate.update("""
                INSERT INTO room_exit (id, direction, source_room_id, target_room_id)
                VALUES (:id, :direction, :sourceRoomId, :targetRoomId)
                """, new MapSqlParameterSource().addValue("id", exit.id()).addValue("direction", exit.direction())
                .addValue("sourceRoomId", exit.sourceRoomId()).addValue("targetRoomId", exit.targetRoomId()));
    }

    public List<RoomExit> findBySourceRoomId(UUID sourceRoomId) {
        return jdbcTemplate.query("SELECT * FROM room_exit WHERE source_room_id = :sourceRoomId",
                Map.of("sourceRoomId", sourceRoomId), MAPPER);
    }

    public Optional<RoomExit> findBySourceRoomIdAndDirection(UUID sourceRoomId, String direction) {
        return jdbcTemplate
                .query("SELECT * FROM room_exit WHERE source_room_id = :sourceRoomId AND direction = :direction",
                        Map.of("sourceRoomId", sourceRoomId, "direction", direction), MAPPER)
                .stream().findFirst();
    }
}

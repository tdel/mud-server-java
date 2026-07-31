package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.RoomExit;

public class RoomExitRowMapper implements RowMapper<RoomExit> {

    @Override
    public RoomExit mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RoomExit(
                rs.getObject("id", UUID.class),
                rs.getString("direction"),
                rs.getObject("source_room_id", UUID.class),
                rs.getObject("target_room_id", UUID.class)
        );
    }
}

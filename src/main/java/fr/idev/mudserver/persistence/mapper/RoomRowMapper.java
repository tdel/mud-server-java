package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.Room;

public class RoomRowMapper implements RowMapper<Room> {

    @Override
    public Room mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Room(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                (Boolean) rs.getObject("is_starting_room"));
    }
}

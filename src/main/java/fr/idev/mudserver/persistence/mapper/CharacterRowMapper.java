package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;

public class CharacterRowMapper implements RowMapper<Character> {

    @Override
    public Character mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Character(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getString("name"),
                rs.getObject("current_room_id", UUID.class),
                Race.valueOf(rs.getString("race")),
                rs.getInt("current_health"),
                rs.getInt("max_health"),
                rs.getInt("current_mana"),
                rs.getInt("max_mana"),
                rs.getInt("strength"),
                rs.getInt("dexterity"),
                rs.getInt("constitution"),
                rs.getInt("intelligence"),
                rs.getInt("wisdom"),
                rs.getInt("charisma")
        );
    }
}

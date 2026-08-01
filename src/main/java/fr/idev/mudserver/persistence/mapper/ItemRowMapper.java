package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;

public class ItemRowMapper implements RowMapper<Item> {

    @Override
    public Item mapRow(ResultSet rs, int rowNum) throws SQLException {
        String slot = rs.getString("slot");
        return new Item(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getObject("room_id", UUID.class), rs.getObject("character_id", UUID.class),
                slot == null ? null : EquipmentSlot.valueOf(slot));
    }
}

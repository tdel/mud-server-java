package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;

public class ItemTemplateRowMapper implements RowMapper<ItemTemplate> {

    @Override
    public ItemTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ItemTemplate(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                ItemType.valueOf(rs.getString("type")),
                rs.getInt("weight")
        );
    }
}

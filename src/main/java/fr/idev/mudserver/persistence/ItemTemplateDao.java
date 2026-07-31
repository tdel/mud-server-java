package fr.idev.mudserver.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.persistence.mapper.ItemTemplateRowMapper;

@Repository
public class ItemTemplateDao {

    private static final ItemTemplateRowMapper MAPPER = new ItemTemplateRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ItemTemplateDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ItemTemplate template) {
        jdbcTemplate.update(
                """
                INSERT INTO item_template (id, name, description, type, weight)
                VALUES (:id, :name, :description, :type, :weight)
                """,
                new MapSqlParameterSource()
                        .addValue("id", template.id())
                        .addValue("name", template.name())
                        .addValue("description", template.description())
                        .addValue("type", template.type().name())
                        .addValue("weight", template.weight())
        );
    }

    public Optional<ItemTemplate> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT * FROM item_template WHERE id = :id",
                Map.of("id", id),
                MAPPER
        ).stream().findFirst();
    }

    public Optional<ItemTemplate> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT * FROM item_template WHERE name = :name",
                Map.of("name", name),
                MAPPER
        ).stream().findFirst();
    }

    public boolean existsById(UUID id) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM item_template WHERE id = :id)",
                Map.of("id", id),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }
}

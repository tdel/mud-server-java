package fr.idev.mudserver.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.persistence.mapper.CharacterRowMapper;

@Repository
public class CharacterDao {

    private static final CharacterRowMapper MAPPER = new CharacterRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CharacterDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Character character) {
        jdbcTemplate.update("""
                INSERT INTO character (
                    id, account_id, name, current_room_id, race,
                    current_health, max_health, current_mana, max_mana,
                    strength, dexterity, constitution, intelligence, wisdom, charisma
                ) VALUES (
                    :id, :accountId, :name, :currentRoomId, :race,
                    :currentHealth, :maxHealth, :currentMana, :maxMana,
                    :strength, :dexterity, :constitution, :intelligence, :wisdom, :charisma
                )
                """, new MapSqlParameterSource().addValue("id", character.getId())
                .addValue("accountId", character.getAccountId()).addValue("name", character.getName())
                .addValue("currentRoomId", character.getCurrentRoomId()).addValue("race", character.getRace().name())
                .addValue("currentHealth", character.getCurrentHealth()).addValue("maxHealth", character.getMaxHealth())
                .addValue("currentMana", character.getCurrentMana()).addValue("maxMana", character.getMaxMana())
                .addValue("strength", character.getStrength()).addValue("dexterity", character.getDexterity())
                .addValue("constitution", character.getConstitution())
                .addValue("intelligence", character.getIntelligence()).addValue("wisdom", character.getWisdom())
                .addValue("charisma", character.getCharisma()));
    }

    public Optional<Character> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM character WHERE id = :id", Map.of("id", id), MAPPER).stream()
                .findFirst();
    }

    public List<Character> findByAccountId(UUID accountId) {
        return jdbcTemplate.query("SELECT * FROM character WHERE account_id = :accountId",
                Map.of("accountId", accountId), MAPPER);
    }

    public Optional<Character> findByAccountIdAndName(UUID accountId, String name) {
        return jdbcTemplate.query("SELECT * FROM character WHERE account_id = :accountId AND name = :name",
                Map.of("accountId", accountId, "name", name), MAPPER).stream().findFirst();
    }

    public void updateCurrentRoom(UUID characterId, UUID roomId) {
        jdbcTemplate.update("UPDATE character SET current_room_id = :roomId WHERE id = :characterId",
                Map.of("characterId", characterId, "roomId", roomId));
    }

    public void deleteById(UUID characterId) {
        jdbcTemplate.update("DELETE FROM character WHERE id = :characterId", Map.of("characterId", characterId));
    }
}

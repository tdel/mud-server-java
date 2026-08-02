package fr.idev.mudserver.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.persistence.mapper.AccountRowMapper;

@Repository
public class AccountDao {

    private static final AccountRowMapper MAPPER = new AccountRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccountDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Account account) {
        jdbcTemplate.update("""
                INSERT INTO account (id, login, password, current_character_id)
                VALUES (:id, :login, :password, :currentCharacterId)
                """,
                new MapSqlParameterSource().addValue("id", account.getId()).addValue("login", account.getLogin())
                        .addValue("password", account.getPassword())
                        .addValue("currentCharacterId", account.getCurrentCharacterId()));
    }

    public Optional<Account> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM account WHERE id = :id", Map.of("id", id), MAPPER).stream()
                .findFirst();
    }

    public Optional<Account> findByLogin(String login) {
        return jdbcTemplate.query("SELECT * FROM account WHERE login = :login", Map.of("login", login), MAPPER).stream()
                .findFirst();
    }

    /**
     * {@code characterId} peut être {@code null} (voir CharacterDelete) —
     * {@link Map#of} refuse les valeurs null.
     */
    public void updateCurrentCharacter(UUID accountId, UUID characterId) {
        jdbcTemplate.update("UPDATE account SET current_character_id = :characterId WHERE id = :accountId",
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("characterId", characterId));
    }
}

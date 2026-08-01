package fr.idev.mudserver.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import fr.idev.mudserver.domain.Account;

public class AccountRowMapper implements RowMapper<Account> {

    @Override
    public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("login"), rs.getString("password"),
                rs.getObject("current_character_id", UUID.class));
    }
}

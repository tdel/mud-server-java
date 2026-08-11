package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ACCOUNT;

import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.persistence.jooq.tables.records.AccountRecord;

@Repository
public class AccountDao {

    private final DSLContext dsl;

    public AccountDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Account account) {
        dsl.insertInto(ACCOUNT, ACCOUNT.ID, ACCOUNT.LOGIN, ACCOUNT.PASSWORD, ACCOUNT.CURRENT_CHARACTER_ID)
                .values(account.getId(), account.getLogin(), account.getPassword(), account.getCurrentCharacterId())
                .execute();
    }

    public Optional<Account> findById(UUID id) {
        return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOptional(AccountDao::toDomain);
    }

    public Optional<Account> findByLogin(String login) {
        return dsl.selectFrom(ACCOUNT).where(ACCOUNT.LOGIN.eq(login)).fetchOptional(AccountDao::toDomain);
    }

    public void updateCurrentCharacter(UUID accountId, UUID characterId) {
        dsl.update(ACCOUNT).set(ACCOUNT.CURRENT_CHARACTER_ID, characterId).where(ACCOUNT.ID.eq(accountId)).execute();
    }

    private static Account toDomain(AccountRecord record) {
        return new Account(record.getId(), record.getLogin(), record.getPassword(), record.getCurrentCharacterId());
    }
}

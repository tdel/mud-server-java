package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.WORLD_INSTANCE;
import static fr.idev.mudserver.persistence.jooq.Tables.WORLD_INSTANCE_MEMBER;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.persistence.jooq.tables.records.WorldInstanceRecord;

@Repository
public class WorldInstanceDao {

    private final DSLContext dsl;

    public WorldInstanceDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(WorldInstance instance) {
        dsl.insertInto(WORLD_INSTANCE, WORLD_INSTANCE.ID, WORLD_INSTANCE.WORLD_TEMPLATE_ID,
                WORLD_INSTANCE.PARTY_LEADER_ACCOUNT_ID, WORLD_INSTANCE.CREATED_AT)
                .values(instance.getId(), instance.getWorldTemplateId(),
                        instance.getPartyLeaderAccountId().orElse(null),
                        OffsetDateTime.ofInstant(instance.getCreatedAt(), ZoneOffset.UTC))
                .execute();
        for (UUID accountId : instance.getMemberAccountIds()) {
            dsl.insertInto(WORLD_INSTANCE_MEMBER, WORLD_INSTANCE_MEMBER.WORLD_INSTANCE_ID,
                    WORLD_INSTANCE_MEMBER.ACCOUNT_ID).values(instance.getId(), accountId).execute();
        }
    }

    public Optional<WorldInstance> findById(UUID id) {
        return dsl.selectFrom(WORLD_INSTANCE).where(WORLD_INSTANCE.ID.eq(id))
                .fetchOptional(record -> toDomain(record, membersOf(id)));
    }

    public Optional<WorldInstance> findByAccountIdAndWorldTemplateId(UUID accountId, UUID worldTemplateId) {
        return dsl.selectFrom(WORLD_INSTANCE).where(WORLD_INSTANCE.WORLD_TEMPLATE_ID.eq(worldTemplateId))
                .and(WORLD_INSTANCE.ID.in(dsl.select(WORLD_INSTANCE_MEMBER.WORLD_INSTANCE_ID)
                        .from(WORLD_INSTANCE_MEMBER).where(WORLD_INSTANCE_MEMBER.ACCOUNT_ID.eq(accountId))))
                .fetchOptional(record -> toDomain(record, membersOf(record.getId())));
    }

    private Set<UUID> membersOf(UUID worldInstanceId) {
        return dsl.select(WORLD_INSTANCE_MEMBER.ACCOUNT_ID).from(WORLD_INSTANCE_MEMBER)
                .where(WORLD_INSTANCE_MEMBER.WORLD_INSTANCE_ID.eq(worldInstanceId))
                .fetchSet(WORLD_INSTANCE_MEMBER.ACCOUNT_ID);
    }

    private static WorldInstance toDomain(WorldInstanceRecord record, Set<UUID> members) {
        return new WorldInstance(record.getId(), record.getWorldTemplateId(), record.getCreatedAt().toInstant(),
                record.getPartyLeaderAccountId(), members);
    }
}

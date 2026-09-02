package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER_ACTIVE_EFFECT;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import app.domain.ActiveEffect;
import app.domain.StatModifier;
import app.persistence.jooq.tables.records.CharacterActiveEffectRecord;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CharacterActiveEffectDao {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public CharacterActiveEffectDao(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public List<ActiveEffect> findByCharacterId(UUID characterId) {
        return dsl.selectFrom(CHARACTER_ACTIVE_EFFECT).where(CHARACTER_ACTIVE_EFFECT.CHARACTER_ID.eq(characterId))
                .fetch(this::toActiveEffect);
    }

    public void upsert(UUID characterId, ActiveEffect effect) {
        delete(characterId, effect.skillId());
        dsl.insertInto(CHARACTER_ACTIVE_EFFECT, CHARACTER_ACTIVE_EFFECT.CHARACTER_ID, CHARACTER_ACTIVE_EFFECT.SKILL_ID,
                CHARACTER_ACTIVE_EFFECT.SKILL_NAME, CHARACTER_ACTIVE_EFFECT.MODIFIERS,
                CHARACTER_ACTIVE_EFFECT.EXPIRES_AT)
                .values(characterId, effect.skillId(), effect.skillName(), writeModifiers(effect.modifiers()),
                        LocalDateTime.ofInstant(effect.expiresAt(), ZoneOffset.UTC))
                .execute();
    }

    public void delete(UUID characterId, UUID skillId) {
        dsl.deleteFrom(CHARACTER_ACTIVE_EFFECT).where(CHARACTER_ACTIVE_EFFECT.CHARACTER_ID.eq(characterId))
                .and(CHARACTER_ACTIVE_EFFECT.SKILL_ID.eq(skillId)).execute();
    }

    private ActiveEffect toActiveEffect(CharacterActiveEffectRecord record) {
        return new ActiveEffect(record.getSkillId(), record.getSkillName(), readModifiers(record.getModifiers()),
                record.getExpiresAt().toInstant(ZoneOffset.UTC));
    }

    private String writeModifiers(List<StatModifier> modifiers) {
        return objectMapper.writeValueAsString(modifiers);
    }

    private List<StatModifier> readModifiers(String json) {
        return objectMapper.readValue(json, new TypeReference<List<StatModifier>>() {
        });
    }
}

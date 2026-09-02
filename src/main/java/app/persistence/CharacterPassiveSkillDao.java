package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER_PASSIVE_SKILL;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CharacterPassiveSkillDao {

    private final DSLContext dsl;

    public CharacterPassiveSkillDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<UUID, Integer> findPassiveSkillLevelsByCharacter(UUID characterId) {
        return dsl.select(CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID, CHARACTER_PASSIVE_SKILL.LEVEL)
                .from(CHARACTER_PASSIVE_SKILL).where(CHARACTER_PASSIVE_SKILL.CHARACTER_ID.eq(characterId)).fetch()
                .stream().collect(Collectors.toMap(row -> row.get(CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID),
                        row -> row.get(CHARACTER_PASSIVE_SKILL.LEVEL)));
    }

    public void insert(UUID characterId, UUID passiveSkillId, int level) {
        dsl.insertInto(CHARACTER_PASSIVE_SKILL, CHARACTER_PASSIVE_SKILL.CHARACTER_ID,
                CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID, CHARACTER_PASSIVE_SKILL.LEVEL)
                .values(characterId, passiveSkillId, level).execute();
    }
}

package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import app.domain.Account;
import app.domain.Spell;
import app.domain.actor.Attribute;
import app.domain.actor.component.ActiveEffect;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.world.ZoneInstance;
import app.domain.world.WorldInstance;
import app.game.catalog.SpellCatalog;
import app.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;
    private final CharacterSpellDao characterSpellDao;
    private final CharacterActiveEffectDao characterActiveEffectDao;
    private final SpellCatalog spellCatalog;

    public CharacterDao(DSLContext dsl, CharacterSpellDao characterSpellDao,
            CharacterActiveEffectDao characterActiveEffectDao, SpellCatalog spellCatalog) {
        this.dsl = dsl;
        this.characterSpellDao = characterSpellDao;
        this.characterActiveEffectDao = characterActiveEffectDao;
        this.spellCatalog = spellCatalog;
    }

    public void insert(CharacterInstance character) {
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_ZONE_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WISDOM, CHARACTER.CHARISMA, CHARACTER.XP, CHARACTER.GOLD,
                CHARACTER.SHORT_REST_COUNT, CHARACTER.MAX_MANA, CHARACTER.CURRENT_MANA)
                .values(character.getId(), character.getAccountId(), character.getName(), character.getCurrentZoneId(),
                        character.getGender().name(), character.getRace().name(), character.getCharacterClass().name(),
                        character.getLevel(), character.getCurrentHealth(), character.getMaxHealth(),
                        character.getAttribute(Attribute.STRENGTH), character.getAttribute(Attribute.DEXTERITY),
                        character.getAttribute(Attribute.CONSTITUTION), character.getAttribute(Attribute.INTELLIGENCE),
                        character.getAttribute(Attribute.WISDOM), character.getAttribute(Attribute.CHARISMA),
                        character.getXp(), character.getInventory().getGold(), character.getShortRestCount(),
                        character.getMaxMana(), character.getCurrentMana())
                .execute();
    }

    public List<CharacterInstance> findAllByAccount(Account account, WorldInstance instance) {
        // toDomain déclenche des requêtes imbriquées (sorts/effets) : .fetch() sans
        // mapper
        // matérialise le Result et libère la connexion avant le mapping, indispensable
        // avec le pool HikariCP à 1 connexion (sinon deadlock).
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(account.getId())).orderBy(CHARACTER.NAME).fetch()
                .stream().map(record -> toDomain(record, account, instance)).toList();
    }

    public Optional<CharacterInstance> findByAccountAndName(Account account, WorldInstance instance, String name) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(account.getId())).and(CHARACTER.NAME.eq(name))
                .fetchOptional().map(record -> toDomain(record, account, instance));
    }

    public void updateCurrentZone(UUID characterId, UUID zoneId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ZONE_ID, zoneId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    public void update(CharacterInstance character) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ZONE_ID, character.getCurrentZoneId())
                .set(CHARACTER.CURRENT_HEALTH, character.getCurrentHealth()).set(CHARACTER.XP, character.getXp())
                .set(CHARACTER.LEVEL, character.getLevel()).set(CHARACTER.MAX_HEALTH, character.getMaxHealth())
                .set(CHARACTER.GOLD, character.getInventory().getGold())
                .set(CHARACTER.SHORT_REST_COUNT, character.getShortRestCount())
                .set(CHARACTER.MAX_MANA, character.getMaxMana()).set(CHARACTER.CURRENT_MANA, character.getCurrentMana())
                .where(CHARACTER.ID.eq(character.getId())).execute();
    }

    public void deleteById(UUID characterId) {
        dsl.deleteFrom(CHARACTER).where(CHARACTER.ID.eq(characterId)).execute();
    }

    private CharacterInstance toDomain(CharacterRecord record, Account account, WorldInstance instance) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STRENGTH, record.getStrength());
        attributes.put(Attribute.DEXTERITY, record.getDexterity());
        attributes.put(Attribute.CONSTITUTION, record.getConstitution());
        attributes.put(Attribute.INTELLIGENCE, record.getIntelligence());
        attributes.put(Attribute.WISDOM, record.getWisdom());
        attributes.put(Attribute.CHARISMA, record.getCharisma());

        CharacterClass characterClass = CharacterClass.valueOf(record.getCharacterClass());
        Race race = Race.valueOf(record.getRace());

        ZoneInstance zone = instance.zoneInstanceForTemplate(record.getCurrentZoneId())
                .or(instance::startingZoneInstance).orElseThrow(() -> new IllegalStateException(
                        "WorldInstance " + instance.getId() + " n'a aucune zone de départ"));

        Set<Spell> knownSpells = characterSpellDao.findSpellIdsByCharacter(record.getId()).stream()
                .map(spellCatalog::getById).collect(Collectors.toSet());
        Instant now = Instant.now();
        List<ActiveEffect> activeEffects = characterActiveEffectDao.findByCharacterId(record.getId()).stream()
                .filter(effect -> effect.expiresAt().isAfter(now)).toList();

        CharacterInstance character = new CharacterInstance(record.getId(), account, record.getName(), zone,
                Gender.valueOf(record.getGender()), race, characterClass, record.getLevel(), record.getCurrentHealth(),
                record.getMaxHealth(), attributes, record.getXp(), record.getGold(), record.getShortRestCount(),
                record.getMaxMana(), record.getCurrentMana(), knownSpells, activeEffects);
        character.setWorldInstance(instance);
        return character;
    }
}

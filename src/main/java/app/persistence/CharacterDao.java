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
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.actor.Attribute;
import app.domain.ActiveEffect;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.Subclass;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.domain.world.WorldInstance;
import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.SkillCatalog;
import app.game.combat.CombatFormulas;
import app.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;
    private final CharacterSkillDao characterSkillDao;
    private final CharacterActiveEffectDao characterActiveEffectDao;
    private final CharacterPassiveSkillDao characterPassiveSkillDao;
    private final SkillCatalog skillCatalog;
    private final PassiveSkillCatalog passiveSkillCatalog;

    public CharacterDao(DSLContext dsl, CharacterSkillDao characterSkillDao,
            CharacterActiveEffectDao characterActiveEffectDao, CharacterPassiveSkillDao characterPassiveSkillDao,
            SkillCatalog skillCatalog, PassiveSkillCatalog passiveSkillCatalog) {
        this.dsl = dsl;
        this.characterSkillDao = characterSkillDao;
        this.characterActiveEffectDao = characterActiveEffectDao;
        this.characterPassiveSkillDao = characterPassiveSkillDao;
        this.skillCatalog = skillCatalog;
        this.passiveSkillCatalog = passiveSkillCatalog;
    }

    public void insert(CharacterInstance character) {
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_MAP_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WIT, CHARACTER.MEN, CHARACTER.XP, CHARACTER.GOLD, CHARACTER.MAX_MANA,
                CHARACTER.CURRENT_MANA, CHARACTER.SUBCLASS_TIER1, CHARACTER.SUBCLASS_TIER2)
                .values(character.getId(), character.getAccountId(), character.getName(), character.getCurrentMapId(),
                        character.getGender().name(), character.getRace().name(), character.getCharacterClass().name(),
                        character.getLevel(), character.getCurrentHealth(), character.getMaxHealth(),
                        character.getAttribute(Attribute.STRENGTH), character.getAttribute(Attribute.DEXTERITY),
                        character.getAttribute(Attribute.CONSTITUTION), character.getAttribute(Attribute.INTELLIGENCE),
                        character.getAttribute(Attribute.WIT), character.getAttribute(Attribute.MEN), character.getXp(),
                        character.getInventory().getGold(), character.getMaxMana(), character.getCurrentMana(),
                        name(character.getSubclassTier1()), name(character.getSubclassTier2()))
                .execute();
    }

    private static String name(Subclass subclass) {
        return subclass == null ? null : subclass.name();
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

    public void updateCurrentMap(UUID characterId, UUID mapId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_MAP_ID, mapId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    public void updatePosition(UUID characterId, double x, double y) {
        dsl.update(CHARACTER).set(CHARACTER.POS_X, x).set(CHARACTER.POS_Y, y).where(CHARACTER.ID.eq(characterId))
                .execute();
    }

    public void update(CharacterInstance character) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_MAP_ID, character.getCurrentMapId())
                .set(CHARACTER.CURRENT_HEALTH, character.getCurrentHealth()).set(CHARACTER.XP, character.getXp())
                .set(CHARACTER.LEVEL, character.getLevel()).set(CHARACTER.MAX_HEALTH, character.getMaxHealth())
                .set(CHARACTER.GOLD, character.getInventory().getGold()).set(CHARACTER.MAX_MANA, character.getMaxMana())
                .set(CHARACTER.CURRENT_MANA, character.getCurrentMana())
                .set(CHARACTER.SUBCLASS_TIER1, name(character.getSubclassTier1()))
                .set(CHARACTER.SUBCLASS_TIER2, name(character.getSubclassTier2()))
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
        attributes.put(Attribute.WIT, record.getWit());
        attributes.put(Attribute.MEN, record.getMen());

        CharacterClass characterClass = CharacterClass.valueOf(record.getCharacterClass());
        Race race = Race.valueOf(record.getRace());

        MapInstance map = instance.mapInstanceForTemplate(record.getCurrentMapId()).or(instance::startingMapInstance)
                .orElseThrow(() -> new IllegalStateException(
                        "WorldInstance " + instance.getId() + " n'a aucune map de départ"));

        Set<ActiveSkill> knownSkills = characterSkillDao.findSkillIdsByCharacter(record.getId()).stream()
                .map(skillCatalog::getById).collect(Collectors.toSet());
        Set<PassiveSkill> knownPassiveSkills = characterPassiveSkillDao.findPassiveSkillIdsByCharacter(record.getId())
                .stream().map(passiveSkillCatalog::getById).collect(Collectors.toSet());
        Instant now = Instant.now();
        List<ActiveEffect> activeEffects = characterActiveEffectDao.findByCharacterId(record.getId()).stream()
                .filter(effect -> effect.expiresAt().isAfter(now)).toList();

        Subclass subclassTier1 = record.getSubclassTier1() == null ? null : Subclass.valueOf(record.getSubclassTier1());
        Subclass subclassTier2 = record.getSubclassTier2() == null ? null : Subclass.valueOf(record.getSubclassTier2());

        int maxHealth = CombatFormulas.maxHealth(characterClass.hitDie(), record.getConstitution(), record.getLevel());
        int maxMana = CombatFormulas.maxMana(characterClass.manaGainPerLevel(), record.getMen(), record.getLevel());

        CharacterInstance character = new CharacterInstance(record.getId(), account, record.getName(), map,
                Gender.valueOf(record.getGender()), race, characterClass, record.getLevel(), record.getCurrentHealth(),
                maxHealth, attributes, record.getXp(), record.getGold(), maxMana, record.getCurrentMana(), knownSkills,
                activeEffects, subclassTier1, subclassTier2, knownPassiveSkills);
        character.setWorldInstance(instance);

        Double posX = record.getPosX();
        Double posY = record.getPosY();
        if (posX != null && posY != null) {
            character.setPosition(new Position(posX, posY));
        }

        return character;
    }
}

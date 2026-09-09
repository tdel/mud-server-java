package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import app.domain.Account;
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.actor.Attribute;
import app.domain.ActiveEffect;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.domain.actor.Subclass;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.domain.world.WorldInstance;
import app.game.catalog.PassiveSkillCatalog;
import app.game.catalog.SkillCatalog;
import app.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;
    private final CharacterSkillDao characterSkillDao;
    private final CharacterActiveEffectDao characterActiveEffectDao;
    private final CharacterPassiveSkillDao characterPassiveSkillDao;
    private final ItemDao itemDao;
    private final SkillCatalog skillCatalog;
    private final PassiveSkillCatalog passiveSkillCatalog;

    public CharacterDao(DSLContext dsl, CharacterSkillDao characterSkillDao,
            CharacterActiveEffectDao characterActiveEffectDao, CharacterPassiveSkillDao characterPassiveSkillDao,
            ItemDao itemDao, SkillCatalog skillCatalog, PassiveSkillCatalog passiveSkillCatalog) {
        this.dsl = dsl;
        this.characterSkillDao = characterSkillDao;
        this.characterActiveEffectDao = characterActiveEffectDao;
        this.characterPassiveSkillDao = characterPassiveSkillDao;
        this.itemDao = itemDao;
        this.skillCatalog = skillCatalog;
        this.passiveSkillCatalog = passiveSkillCatalog;
    }

    public void insert(PlayerInstance character) {
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_MAP_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WIT, CHARACTER.MEN, CHARACTER.XP, CHARACTER.GOLD, CHARACTER.MAX_MANA,
                CHARACTER.CURRENT_MANA, CHARACTER.SUBCLASS_TIER1, CHARACTER.SUBCLASS_TIER2,
                CHARACTER.ACTIVE_SOULSHOT_GRADE, CHARACTER.ACTIVE_SPIRITSHOT_GRADE, CHARACTER.KARMA, CHARACTER.PK_COUNT,
                CHARACTER.PVP_COUNT, CHARACTER.PVP_FLAGGED)
                .values(character.getId(), character.getAccountId(), character.getName(),
                        character.getMotionSystem().getCurrentMap().getTemplateId(),
                        character.getAppearanceSystem().getGender().name(),
                        character.getAppearanceSystem().getRace().name(),
                        character.getClassSystem().getCharacterClass().name(), character.getLevelingSystem().getLevel(),
                        character.getResourceSystem().getCurrentHealth(), character.getResourceSystem().getMaxHealth(),
                        character.getAttributeSystem().getAttribute(Attribute.STR),
                        character.getAttributeSystem().getAttribute(Attribute.DEX),
                        character.getAttributeSystem().getAttribute(Attribute.CON),
                        character.getAttributeSystem().getAttribute(Attribute.INT),
                        character.getAttributeSystem().getAttribute(Attribute.WIT),
                        character.getAttributeSystem().getAttribute(Attribute.MEN),
                        character.getLevelingSystem().getXp(), character.getInventorySystem().getGold(),
                        character.getResourceSystem().getMaxMana(), character.getResourceSystem().getCurrentMana(),
                        name(character.getClassSystem().getSubclass(1)),
                        name(character.getClassSystem().getSubclass(2)),
                        gradeName(character.getInventorySystem().getActiveSoulshotGrade()),
                        gradeName(character.getInventorySystem().getActiveSpiritshotGrade()),
                        character.getPvpSystem().getKarma(), character.getPvpSystem().getPkCount(),
                        character.getPvpSystem().getPvpCount(), character.getPvpSystem().isPvpFlagged())
                .execute();
    }

    private static String name(Subclass subclass) {
        return subclass == null ? null : subclass.name();
    }

    private static String gradeName(ItemGrade grade) {
        return grade == null ? null : grade.name();
    }

    private static ItemGrade parseGrade(String grade) {
        return grade == null ? null : ItemGrade.valueOf(grade);
    }

    public List<PlayerInstance> findAllByAccount(Account account, WorldInstance instance) {
        // toDomain déclenche des requêtes imbriquées (sorts/effets) : .fetch() sans
        // mapper
        // matérialise le Result et libère la connexion avant le mapping, indispensable
        // avec le pool HikariCP à 1 connexion (sinon deadlock).
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(account.getId())).orderBy(CHARACTER.NAME).fetch()
                .stream().map(record -> toDomain(record, account, instance)).toList();
    }

    public Optional<PlayerInstance> findByAccountAndName(Account account, WorldInstance instance, String name) {
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

    public void update(PlayerInstance character) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_MAP_ID, character.getMotionSystem().getCurrentMap().getTemplateId())
                .set(CHARACTER.CURRENT_HEALTH, character.getResourceSystem().getCurrentHealth())
                .set(CHARACTER.XP, character.getLevelingSystem().getXp())
                .set(CHARACTER.LEVEL, character.getLevelingSystem().getLevel())
                .set(CHARACTER.MAX_HEALTH, character.getResourceSystem().getMaxHealth())
                .set(CHARACTER.GOLD, character.getInventorySystem().getGold())
                .set(CHARACTER.MAX_MANA, character.getResourceSystem().getMaxMana())
                .set(CHARACTER.CURRENT_MANA, character.getResourceSystem().getCurrentMana())
                .set(CHARACTER.SUBCLASS_TIER1, name(character.getClassSystem().getSubclass(1)))
                .set(CHARACTER.SUBCLASS_TIER2, name(character.getClassSystem().getSubclass(2)))
                .set(CHARACTER.ACTIVE_SOULSHOT_GRADE,
                        gradeName(character.getInventorySystem().getActiveSoulshotGrade()))
                .set(CHARACTER.ACTIVE_SPIRITSHOT_GRADE,
                        gradeName(character.getInventorySystem().getActiveSpiritshotGrade()))
                .set(CHARACTER.KARMA, character.getPvpSystem().getKarma())
                .set(CHARACTER.PK_COUNT, character.getPvpSystem().getPkCount())
                .set(CHARACTER.PVP_COUNT, character.getPvpSystem().getPvpCount())
                .set(CHARACTER.PVP_FLAGGED, character.getPvpSystem().isPvpFlagged())
                .where(CHARACTER.ID.eq(character.getId())).execute();
    }

    public void deleteById(UUID characterId) {
        dsl.deleteFrom(CHARACTER).where(CHARACTER.ID.eq(characterId)).execute();
    }

    private PlayerInstance toDomain(CharacterRecord record, Account account, WorldInstance instance) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STR, record.getStrength());
        attributes.put(Attribute.DEX, record.getDexterity());
        attributes.put(Attribute.CON, record.getConstitution());
        attributes.put(Attribute.INT, record.getIntelligence());
        attributes.put(Attribute.WIT, record.getWit());
        attributes.put(Attribute.MEN, record.getMen());

        CharacterClass characterClass = CharacterClass.valueOf(record.getCharacterClass());
        Race race = Race.valueOf(record.getRace());

        MapInstance map = instance.mapInstanceForTemplate(record.getCurrentMapId()).or(instance::startingMapInstance)
                .orElseThrow(() -> new IllegalStateException(
                        "WorldInstance " + instance.getId() + " n'a aucune map de départ"));

        Map<ActiveSkill, Integer> knownSkills = characterSkillDao.findByCharacter(record.getId()).stream()
                .collect(Collectors.toMap(row -> skillCatalog.getById(row.skillId()),
                        CharacterSkillDao.CharacterSkillRow::level));
        Map<PassiveSkill, Integer> knownPassiveSkills = characterPassiveSkillDao
                .findPassiveSkillLevelsByCharacter(record.getId()).entrySet().stream()
                .collect(Collectors.toMap(entry -> passiveSkillCatalog.getById(entry.getKey()), Map.Entry::getValue));
        Instant now = Instant.now();
        List<ActiveEffect> activeEffects = characterActiveEffectDao.findByCharacterId(record.getId()).stream()
                .filter(effect -> effect.expiresAt().isAfter(now)).toList();

        List<Subclass> subclasses = new ArrayList<>();
        if (record.getSubclassTier1() != null) {
            subclasses.add(Subclass.valueOf(record.getSubclassTier1()));
        }
        if (record.getSubclassTier2() != null) {
            subclasses.add(Subclass.valueOf(record.getSubclassTier2()));
        }

        int maxHealth = characterClass.maxHealth(record.getConstitution(), record.getLevel());
        int maxMana = characterClass.maxMana(record.getMen(), record.getLevel());

        List<Item> items = itemDao.findByCharacterId(record.getId());

        PlayerInstance character = new PlayerInstance(record.getId(), account, record.getName(), map,
                Gender.valueOf(record.getGender()), race, characterClass, record.getLevel(), record.getCurrentHealth(),
                maxHealth, attributes, record.getXp(), record.getGold(), maxMana, record.getCurrentMana(), knownSkills,
                activeEffects, subclasses, knownPassiveSkills, items, parseGrade(record.getActiveSoulshotGrade()),
                parseGrade(record.getActiveSpiritshotGrade()), record.getKarma(), record.getPkCount(),
                record.getPvpCount(), record.getPvpFlagged());
        character.setWorldInstance(instance);

        Double posX = record.getPosX();
        Double posY = record.getPosY();
        if (posX != null && posY != null) {
            character.getMotionSystem().setPosition(new Position(posX, posY));
        }

        return character;
    }
}

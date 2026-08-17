package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.CHARACTER;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.AttributeComponent;
import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.component.InventoryComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.component.RestComponent;
import fr.idev.mudserver.domain.actor.component.WorldComponent;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;

    public CharacterDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(CharacterInstance character) {
        UUID worldInstanceId = character.findComponent(WorldComponent.class)
                .map(component -> component.worldInstance().getId()).orElse(WorldInstance.DEFAULT_ID);
        CombatComponent combat = character.component(CombatComponent.class);
        UUID currentRoomId = character.component(PositionComponent.class).currentRoom().getTemplateId();
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_ROOM_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WISDOM, CHARACTER.CHARISMA, CHARACTER.XP, CHARACTER.GOLD,
                CHARACTER.SHORT_REST_COUNT, CHARACTER.WORLD_INSTANCE_ID)
                .values(character.getId(), character.getAccountId(), character.getName(), currentRoomId,
                        character.component(AppearanceComponent.class).gender().name(),
                        character.component(AppearanceComponent.class).race().name(),
                        character.component(AppearanceComponent.class).characterClass().name(),
                        character.component(LevelingComponent.class).level(), combat.currentHealth(),
                        combat.maxHealth(), character.component(AttributeComponent.class).valueOf(Attribute.STRENGTH),
                        character.component(AttributeComponent.class).valueOf(Attribute.DEXTERITY),
                        character.component(AttributeComponent.class).valueOf(Attribute.CONSTITUTION),
                        character.component(AttributeComponent.class).valueOf(Attribute.INTELLIGENCE),
                        character.component(AttributeComponent.class).valueOf(Attribute.WISDOM),
                        character.component(AttributeComponent.class).valueOf(Attribute.CHARISMA),
                        character.component(LevelingComponent.class).xp(),
                        character.component(InventoryComponent.class).gold(),
                        character.component(RestComponent.class).shortRestCount(), worldInstanceId)
                .execute();
    }

    public Optional<CharacterInstance> findByAccountAndWorldInstance(Account account, WorldInstance instance) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(account.getId()))
                .and(CHARACTER.WORLD_INSTANCE_ID.eq(instance.getId()))
                .fetchOptional(record -> toDomain(record, account, instance));
    }

    public Optional<CharacterInstance> findByAccountAndWorldInstanceAndName(Account account, WorldInstance instance,
            String name) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(account.getId()))
                .and(CHARACTER.WORLD_INSTANCE_ID.eq(instance.getId())).and(CHARACTER.NAME.eq(name))
                .fetchOptional(record -> toDomain(record, account, instance));
    }

    public void updateCurrentRoom(UUID characterId, UUID roomId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, roomId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    public void update(CharacterInstance character) {
        LevelingComponent leveling = character.component(LevelingComponent.class);
        CombatComponent combat = character.component(CombatComponent.class);
        UUID currentRoomId = character.component(PositionComponent.class).currentRoom().getTemplateId();
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, currentRoomId)
                .set(CHARACTER.CURRENT_HEALTH, combat.currentHealth()).set(CHARACTER.XP, leveling.xp())
                .set(CHARACTER.LEVEL, leveling.level()).set(CHARACTER.MAX_HEALTH, combat.maxHealth())
                .set(CHARACTER.GOLD, character.component(InventoryComponent.class).gold())
                .set(CHARACTER.SHORT_REST_COUNT, character.component(RestComponent.class).shortRestCount())
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

        RoomInstance room = instance.roomInstanceForTemplate(record.getCurrentRoomId())
                .or(instance::startingRoomInstance).orElseThrow(() -> new IllegalStateException(
                        "WorldInstance " + instance.getId() + " n'a aucune room de départ"));

        CharacterInstance character = new CharacterInstance(record.getId(), account, record.getName(), room,
                Gender.valueOf(record.getGender()), race, characterClass, record.getLevel(), record.getCurrentHealth(),
                record.getMaxHealth(), attributes, record.getXp(), record.getGold(), record.getShortRestCount());
        character.attachComponent(new WorldComponent(instance));
        return character;
    }
}

package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.CHARACTER;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;

    public CharacterDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Replie sur {@link WorldInstance#DEFAULT_ID} quand {@code character
     * .getWorldInstanceId()} est {@code null} : {@code GamePlayer} n'a pas gagné de
     * paramètre de constructeur pour ce champ (aurait fallu toucher tous les sites
     * de test qui le construisent directement) — seul
     * {@code GameWorld.createCharacter} le renseigne explicitement aujourd'hui.
     * Correct tant qu'une seule {@code WorldInstance} existe (avant le Lobby, à
     * venir) : tout personnage inséré sans instance explicite appartient de fait à
     * celle-là.
     */
    public void insert(GamePlayer character) {
        UUID worldInstanceId = character.getWorldInstanceId() != null
                ? character.getWorldInstanceId()
                : WorldInstance.DEFAULT_ID;
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_ROOM_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WISDOM, CHARACTER.CHARISMA, CHARACTER.XP, CHARACTER.GOLD,
                CHARACTER.SHORT_REST_COUNT, CHARACTER.WORLD_INSTANCE_ID)
                .values(character.getId(), character.getAccountId(), character.getName(), character.getCurrentRoomId(),
                        character.getGender().name(), character.getRace().name(), character.getCharacterClass().name(),
                        character.getLevel(), character.getCurrentHealth(), character.getMaxHealth(),
                        character.getAttribute(Attribute.STRENGTH), character.getAttribute(Attribute.DEXTERITY),
                        character.getAttribute(Attribute.CONSTITUTION), character.getAttribute(Attribute.INTELLIGENCE),
                        character.getAttribute(Attribute.WISDOM), character.getAttribute(Attribute.CHARISMA),
                        character.getXp(), character.getInventory().getGold(), character.getShortRestCount(),
                        worldInstanceId)
                .execute();
    }

    public Optional<GamePlayer> findById(UUID id) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ID.eq(id)).fetchOptional(this::toDomain);
    }

    /**
     * Renvoie {@code Optional}, pas une liste : au plus un personnage par
     * {@code (account_id, world_instance_id)} (règle actée, voir
     * {@code multi-world.md} Phase C) — remplace {@code findByAccountId}, dont le
     * seul appelant ({@code controller.authed.CharacterList}, supprimé) reposait
     * sur un vrai listing qui n'a plus lieu d'être une fois cette règle en place.
     */
    public Optional<GamePlayer> findByAccountIdAndWorldInstanceId(UUID accountId, UUID worldInstanceId) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId))
                .and(CHARACTER.WORLD_INSTANCE_ID.eq(worldInstanceId)).fetchOptional(this::toDomain);
    }

    public Optional<GamePlayer> findByAccountIdAndName(UUID accountId, UUID worldInstanceId, String name) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId))
                .and(CHARACTER.WORLD_INSTANCE_ID.eq(worldInstanceId)).and(CHARACTER.NAME.eq(name))
                .fetchOptional(this::toDomain);
    }

    public void updateCurrentRoom(UUID characterId, UUID roomId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, roomId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    /**
     * Ne persiste que les champs qui évoluent réellement en jeu (position, santé,
     * XP, niveau, or, repos courts pris) ; race/stats/nom restent figés à la
     * création, pas besoin de les réécrire.
     */
    public void update(GamePlayer character) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, character.getCurrentRoomId())
                .set(CHARACTER.CURRENT_HEALTH, character.getCurrentHealth()).set(CHARACTER.XP, character.getXp())
                .set(CHARACTER.LEVEL, character.getLevel()).set(CHARACTER.MAX_HEALTH, character.getMaxHealth())
                .set(CHARACTER.GOLD, character.getInventory().getGold())
                .set(CHARACTER.SHORT_REST_COUNT, character.getShortRestCount())
                .where(CHARACTER.ID.eq(character.getId())).execute();
    }

    public void deleteById(UUID characterId) {
        dsl.deleteFrom(CHARACTER).where(CHARACTER.ID.eq(characterId)).execute();
    }

    private GamePlayer toDomain(CharacterRecord record) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STRENGTH, record.getStrength());
        attributes.put(Attribute.DEXTERITY, record.getDexterity());
        attributes.put(Attribute.CONSTITUTION, record.getConstitution());
        attributes.put(Attribute.INTELLIGENCE, record.getIntelligence());
        attributes.put(Attribute.WISDOM, record.getWisdom());
        attributes.put(Attribute.CHARISMA, record.getCharisma());

        CharacterClass characterClass = CharacterClass.valueOf(record.getCharacterClass());
        Race race = Race.valueOf(record.getRace());

        GamePlayer character = new GamePlayer(record.getId(), record.getAccountId(), record.getName(),
                record.getCurrentRoomId(), Gender.valueOf(record.getGender()), race, characterClass, record.getLevel(),
                record.getCurrentHealth(), record.getMaxHealth(), attributes, record.getXp(), record.getGold(),
                record.getShortRestCount());
        character.setWorldInstanceId(record.getWorldInstanceId());
        return character;
    }
}

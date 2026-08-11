package fr.idev.mudserver.game.actor;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fr.idev.mudserver.AbstractIntegrationTest;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.MonsterTemplate;
import fr.idev.mudserver.domain.actor.MonsterTemplate.LootTableEntry;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.domain.actor.TestAttributes;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.ItemService;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import fr.idev.mudserver.persistence.ItemDao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code LootService.onCharacterDied} est {@code @Transactional} : suit le même
 * patron que {@code CharacterServiceTest}/{@code ItemServiceTest} (classe
 * {@code @Transactional}, tout se rollback à la fin de chaque test). Déclenché
 * via le vrai chemin de domaine ({@code monster.takeDamage}) plutôt qu'en
 * appelant {@code onCharacterDied} directement, comme {@code CombatEngineTest}
 * le fait pour {@code CharacterDied}. Les entrées de table de butin utilisent
 * des probabilités limites (0.0/1.0) pour rester déterministes sans mocker
 * {@code DiceRoller} (même convention que
 * {@code GamePlayerTest}/{@code CombatEngineTest}) : {@code
 * Random#nextDouble()} ne retourne jamais exactement 1.0, donc {@code
 * dropChance = 1.0} réussit toujours et {@code dropChance = 0.0} échoue
 * toujours.
 */
@Transactional
class LootServiceTest extends AbstractIntegrationTest {

    // Templates réels de data/items.json, déjà utilisés par
    // ItemServiceTest#warmItemTemplatesLoadsTheRealCatalogFromJson — évite de
    // dépendre de ItemService#registerTemplate, package-private (accessible
    // seulement depuis fr.idev.mudserver.game).
    private static final UUID POTION_TEMPLATE_ID = UUID.fromString("019fa0a5-80bf-7e84-87bf-5cf699c00315");
    private static final UUID SWORD_TEMPLATE_ID = UUID.fromString("019fa0a5-80c0-7035-9c2d-113b09a275df");

    @Autowired
    private ItemService itemService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private CharacterDao characterDao;

    @Autowired
    private ItemDao itemDao;

    @Test
    void killerReceivesGuaranteedGoldRewardRegardlessOfLootTable() {
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 5, List.of());

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getGold()).isEqualTo(5);
        assertThat(characterDao.findById(killer.getId()).map(c -> c.getInventory().getGold())).contains(5);
    }

    @Test
    void zeroGoldRewardGrantsNoGold() {
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 0, List.of());

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getGold()).isZero();
    }

    @Test
    void hundredPercentDropChanceEntryAlwaysProducesALootedItemForTheKiller() {
        itemService.warmItemTemplates();
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 0, List.of(new LootTableEntry(POTION_TEMPLATE_ID, 1.0)));

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getItems()).extracting(Item::getTemplateId)
                .containsExactly(POTION_TEMPLATE_ID);
        List<Item> persisted = itemDao.findByCharacterId(killer.getId());
        assertThat(persisted).extracting(Item::getTemplateId).containsExactly(POTION_TEMPLATE_ID);
    }

    @Test
    void zeroPercentDropChanceEntryNeverProducesALootedItem() {
        itemService.warmItemTemplates();
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 0, List.of(new LootTableEntry(POTION_TEMPLATE_ID, 0.0)));

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getItems()).isEmpty();
        assertThat(itemDao.findByCharacterId(killer.getId())).isEmpty();
    }

    @Test
    void emptyLootTableGrantsNoItemsAndThrowsNoException() {
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 0, List.of());

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getItems()).isEmpty();
    }

    @Test
    void multipleIndependentLootEntriesAreEachRolledSeparately() {
        itemService.warmItemTemplates();
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 0,
                List.of(new LootTableEntry(POTION_TEMPLATE_ID, 1.0), new LootTableEntry(SWORD_TEMPLATE_ID, 0.0)));

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getItems()).extracting(Item::getTemplateId)
                .containsExactly(POTION_TEMPLATE_ID);
    }

    @Test
    void goldAndLootAreBothGrantedInTheSameKill() {
        itemService.warmItemTemplates();
        GamePlayer killer = killer();
        GameMonster monster = monster(killer.getCurrentRoom(), 7, List.of(new LootTableEntry(POTION_TEMPLATE_ID, 1.0)));

        monster.takeDamage(9999, killer);

        assertThat(killer.getInventory().getGold()).isEqualTo(7);
        assertThat(killer.getInventory().getItems()).extracting(Item::getTemplateId)
                .containsExactly(POTION_TEMPLATE_ID);
    }

    private GamePlayer killer() {
        Account account = new Account(UUID.randomUUID(), "chasseur-" + UUID.randomUUID(), "hashed-password", null);
        accountDao.insert(account);
        GamePlayer character = new GamePlayer(UUID.randomUUID(), account.getId(), "Chasseur", UUID.randomUUID(),
                Gender.MAN, Race.HUMAN, CharacterClass.FIGHTER, 1, 10, 10, TestAttributes.of(10, 10, 10, 10, 10, 10), 0,
                0);
        characterDao.insert(character);
        new RoomInstance(UUID.randomUUID(), "Clairière", "...", null).join(character);
        return character;
    }

    private GameMonster monster(RoomInstance room, int goldReward, List<LootTableEntry> lootTable) {
        MonsterTemplate template = new MonsterTemplate(UUID.randomUUID(), "Gobelin " + UUID.randomUUID(),
                "Un gobelin hostile", 1, TestAttributes.of(10, 10, 10, 10, 10, 10), 10, 0, "1d6", goldReward, lootTable,
                0);
        GameMonster monster = new GameMonster(UUID.randomUUID(), template.getName(), template.getId(), room.getId(),
                template.getAttributes(), 1);
        monster.attachTemplate(template);
        monster.setCurrentRoom(room);
        room.addMonster(monster);
        return monster;
    }
}

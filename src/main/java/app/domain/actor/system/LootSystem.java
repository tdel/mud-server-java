package app.domain.actor.system;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.Party;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.domain.item.Item;
import app.domain.item.LootResult;
import app.domain.item.LootTableEntry;
import app.game.Randomizer;

public final class LootSystem {

    private static final Logger log = LoggerFactory.getLogger(LootSystem.class);

    private final int xpReward;
    private final int goldReward;
    private final List<LootTableEntry> lootTable;

    public LootSystem(AbstractCharacter character, int xpReward, int goldReward, List<LootTableEntry> lootTable) {
        this.xpReward = xpReward;
        this.goldReward = goldReward;
        this.lootTable = lootTable;
    }

    private LootResult rollLoot(CharacterInstance killer) {
        List<Item> items = new ArrayList<>();
        for (LootTableEntry entry : lootTable) {
            if (Randomizer.rollChance(entry.dropChance())) {
                items.add(new Item(UUID.randomUUID(), entry.itemTemplate(), killer, null));
            }
        }
        return new LootResult(goldReward, items);
    }

    public LootResult grantLootTo(CharacterInstance killer, Party party, List<CharacterInstance> eligibleMembers,
            double goldShareMultiplier) {
        LootResult loot = rollLoot(killer);

        if (loot.gold() > 0) {
            int perMemberGold = (int) (loot.gold() * goldShareMultiplier) / eligibleMembers.size();
            for (CharacterInstance member : eligibleMembers) {
                member.getInventorySystem().receiveGold(perMemberGold);
            }
            log.info("loot.gold_dropped killer={} totalGold={} partySize={} perMemberGold={}", killer.getName(),
                    loot.gold(), eligibleMembers.size(), perMemberGold);
        }

        for (Item item : loot.items()) {
            CharacterInstance recipient = party != null ? party.nextLootRecipient(eligibleMembers) : killer;
            recipient.getInventorySystem().receiveLootItem(item);
            log.info("loot.item_dropped killer={} recipient={} item={}", killer.getName(), recipient.getName(),
                    item.getName());
        }

        return loot;
    }

    public int getXpReward() {
        return xpReward;
    }
}

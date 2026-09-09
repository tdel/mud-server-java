package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.SkillElement;
import app.domain.StatModifier;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.domain.actor.event.ShotActivated;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.EquipmentItem;
import app.domain.item.EquipmentSlot;
import app.domain.item.Item;
import app.domain.item.ItemExpectation;
import app.domain.item.ItemGrade;
import app.domain.item.ItemSet;
import app.domain.item.ItemType;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.GoldLooted;
import app.network.message.ingame.GoldSpent;
import app.network.message.ingame.ShotUsed;
import app.network.message.ingame.SoulshotUsed;
import app.network.message.ingame.SpiritshotUsed;

public final class InventorySystem {

    private static final UUID GRADE_PENALTY_EFFECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AbstractCharacter character;
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private int gold;
    private volatile ItemGrade activeSoulshotGrade;
    private volatile ItemGrade activeSpiritshotGrade;

    public InventorySystem(AbstractCharacter character, int gold, List<Item> items, ItemGrade activeSoulshotGrade,
            ItemGrade activeSpiritshotGrade) {
        this.character = character;
        this.gold = gold;
        this.items.addAll(items);
        items.forEach(item -> item.attachOwner(this.character));
        this.activeSoulshotGrade = activeSoulshotGrade;
        this.activeSpiritshotGrade = activeSpiritshotGrade;
    }

    public ItemGrade getActiveSoulshotGrade() {
        return activeSoulshotGrade;
    }

    public void setActiveSoulshotGrade(ItemGrade activeSoulshotGrade) {
        this.activeSoulshotGrade = activeSoulshotGrade;
    }

    public ItemGrade getActiveSpiritshotGrade() {
        return activeSpiritshotGrade;
    }

    public void setActiveSpiritshotGrade(ItemGrade activeSpiritshotGrade) {
        this.activeSpiritshotGrade = activeSpiritshotGrade;
    }

    public int getGold() {
        return gold;
    }

    public void addGold(int amount) {
        this.gold += amount;
    }

    public boolean trySpendGold(int amount) {
        if (gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Optional<Item> findOneById(UUID id) {
        return items.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public List<Item> getEquippedItems() {
        return items.stream().filter(item -> item.getSlot() != null).toList();
    }

    public Optional<Item> getEquippedWeapon() {
        return getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
    }

    public int getElementalResistance(SkillElement element) {
        return getEquippedItems().stream().mapToInt(item -> item.getElementalResistances().getOrDefault(element, 0))
                .sum();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
    }

    // Un item stackable (soulshot/spiritshot) n'a qu'une seule pile par type+grade
    // dans l'inventaire — jamais équipé, donc getSlot() == null suffit à
    // identifier la pile.
    public Optional<Item> findStackable(ItemType type, ItemGrade grade) {
        return items.stream()
                .filter(item -> item.getSlot() == null && item.getType() == type && item.getGrade() == grade)
                .findFirst();
    }

    public void receiveGold(int amount) {
        // Seuls les joueurs ramassent de l'or : receiveGold n'est appelé que sur des
        // PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        addGold(amount);
        character.send(new GoldLooted(amount));
        DomainEventPublisher.publish(new CharacterReceivedGold(player, amount));
    }

    public void receiveLootItem(Item item) {
        // Seuls les joueurs ramassent des objets : receiveLootItem n'est appelé que sur
        // des PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        Optional<Item> stack = mergeIntoExistingStack(item);
        if (stack.isPresent()) {
            DomainEventPublisher.publish(new CharacterLootedItem(player, stack.get(), true));
            return;
        }
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new CharacterLootedItem(player, item, false));
    }

    public boolean buyItem(Item item, int price) {
        // Seuls les joueurs achètent : buyItem n'est appelé que sur des PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        if (!trySpendGold(price)) {
            return false;
        }
        character.send(new GoldSpent(price));
        DomainEventPublisher.publish(new CharacterSpentGold(player, price));
        Optional<Item> stack = mergeIntoExistingStack(item);
        if (stack.isPresent()) {
            DomainEventPublisher.publish(new ItemPurchased(player, stack.get(), price, true));
            return true;
        }
        item.setCharacter(character);
        addItem(item);
        DomainEventPublisher.publish(new ItemPurchased(player, item, price, false));
        return true;
    }

    // Fusionne un item stackable fraîchement acquis (loot/achat) dans une pile
    // existante s'il y en a déjà une — sinon laisse l'appelant l'ajouter comme
    // nouvel item. Retourne la pile mise à jour si un merge a eu lieu.
    private Optional<Item> mergeIntoExistingStack(Item item) {
        if (!item.getType().stackable()) {
            return Optional.empty();
        }
        return findStackable(item.getType(), item.getGrade()).map(existing -> {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            return existing;
        });
    }

    // Nombre de charges consommées par activation : porté par l'arme équipée
    // (cf. EquipmentItem.getShotConsumption), à mains nues 1 charge — jamais une
    // constante fixe, cf. plan Soulshot/Spiritshot.
    public ConsumeShotOutcome consumeShot(ItemType shotType, ItemGrade grade) {
        // Seuls les joueurs utilisent des soulshots/spiritshots : consumeShot n'est
        // appelé que sur des
        // PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        Optional<Item> stack = findStackable(shotType, grade);
        if (stack.isEmpty()) {
            return new ConsumeShotOutcome.OutOfStock();
        }
        Item item = stack.get();
        int count = getEquippedWeapon().map(Item::getShotConsumption).orElse(1);
        if (item.getQuantity() < count) {
            return new ConsumeShotOutcome.OutOfStock();
        }
        int remaining = item.getQuantity() - count;
        if (remaining <= 0) {
            removeItem(item);
        } else {
            item.setQuantity(remaining);
        }
        int remainingQuantity = Math.max(0, remaining);
        character.send(new ShotUsed(shotType, grade, remainingQuantity));
        if (shotType == ItemType.SOULSHOT) {
            character.broadcast(new SoulshotUsed(character.getId(), character.getName(), grade), player);
        } else {
            character.broadcast(new SpiritshotUsed(character.getId(), character.getName(), grade), player);
        }
        DomainEventPublisher.publish(new ShotActivated(player, item, shotType, grade, remainingQuantity));
        return new ConsumeShotOutcome.Consumed(item, count);
    }

    public sealed interface ConsumeShotOutcome {

        record Consumed(Item item, int count) implements ConsumeShotOutcome {
        }

        record OutOfStock() implements ConsumeShotOutcome {
        }
    }

    public void discardItem(Item item) {
        // Seuls les joueurs jettent des objets : discardItem n'est appelé que sur des
        // PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        removeItem(item);
        DomainEventPublisher.publish(new ItemDiscarded(player, item));
    }

    public Optional<EquipmentSlot> equipItem(Item item) {
        // Seuls les joueurs équipent des objets : equipItem n'est appelé que sur des
        // PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        List<EquipmentSlot> candidates = item.getType().equipmentSlots();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Item> equipped = getEquippedItems();
        EquipmentSlot slot = candidates.stream()
                .filter(candidate -> equipped.stream().noneMatch(existing -> existing.getSlot() == candidate))
                .findFirst().orElse(candidates.get(0));

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : equipped) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot);
        DomainEventPublisher.publish(new GamePlayerEquippedItem(player, item, slot, previousOccupants));
        recomputeGradePenalty();
        character.getStatSystem().recomputeStats(character.getAttributeSystem().getAttributes(),
                character.getLevelingSystem().getLevel(), this);
        return Optional.of(slot);
    }

    public void unequipItem(Item item) {
        // Seuls les joueurs déséquipent des objets : unequipItem n'est appelé que sur
        // des PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(player, item));
        recomputeGradePenalty();
        character.getStatSystem().recomputeStats(character.getAttributeSystem().getAttributes(),
                character.getLevelingSystem().getLevel(), this);
    }

    // Ne dépend que des paramètres reçus (aucun accès à `this`) : appelable
    // avant que le InventorySystem du personnage n'existe (PlayerInstance
    // l'appelle avant super(...)), et réutilisée par
    // StatSystem.recomputeStats() pour les recalculs post-construction.
    public static Map<ModifiedStat, Integer> computeBaseStats(Map<Attribute, Integer> attributes, int level,
            List<Item> items, int baseSpeed) {
        List<Item> equipped = items.stream().filter(item -> item.getSlot() != null).toList();
        Optional<Item> weapon = equipped.stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();

        int weaponPAtk = weapon.map(Item::getPAtk).orElse(CombatFormulas.UNARMED_PATK);
        int weaponMAtk = weapon.map(Item::getMAtk).orElse(0);
        int weaponAtkSpd = weapon.map(Item::getAtkSpd).orElse(CombatFormulas.BASE_ATK_SPD);
        int armorPDefSum = equipped.stream().mapToInt(Item::getPDef).sum();
        int armorMDefSum = equipped.stream().mapToInt(Item::getMDef).sum();
        int accuracyItemBonus = equipped.stream().mapToInt(Item::getAccuracyBonus).sum();
        int evasionItemBonus = equipped.stream().mapToInt(Item::getEvasionBonus).sum();
        int critItemBonus = equipped.stream().mapToInt(Item::getCritBonus).sum();
        int armorWeightPenalty = equipped.stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(item -> CombatFormulas.armorWeightPenalty(item.getArmorCategory())).orElse(0);

        Map<ModifiedStat, Integer> stats = CombatFormulas.baseStats(weaponPAtk, weaponMAtk, armorPDefSum, armorMDefSum,
                accuracyItemBonus, evasionItemBonus, critItemBonus, armorWeightPenalty, weaponAtkSpd, attributes,
                level);
        stats.put(ModifiedStat.SPEED, baseSpeed);
        return stats;
    }

    public Map<ModifiedStat, Integer> computeSetBonuses() {
        Map<String, Long> equippedCountBySetId = getEquippedItems().stream().map(Item::getSetId)
                .filter(Objects::nonNull).collect(Collectors.groupingBy(setId -> setId, Collectors.counting()));

        Map<ModifiedStat, Integer> modifiers = new EnumMap<>(ModifiedStat.class);
        for (Map.Entry<String, Long> entry : equippedCountBySetId.entrySet()) {
            ItemSet set = ItemSetCatalogHolder.getById(entry.getKey());
            int piecesEquipped = entry.getValue().intValue();
            for (Map.Entry<Integer, Map<ModifiedStat, Integer>> tier : set.bonusByPieceCount().entrySet()) {
                if (piecesEquipped >= tier.getKey()) {
                    tier.getValue().forEach((stat, amount) -> modifiers.merge(stat, amount, Integer::sum));
                }
            }
        }
        return modifiers;
    }

    // Marqueur unique dans ActiveEffects tant qu'au moins un objet équipé a un
    // ItemExpectation (cf. EquipmentItem.getExpectation) non rempli — typiquement
    // un grade au-delà de l'expertise débloquée (SkillSystem.passiveLevelOf). Pas
    // de vraie expiration (le malus dure tant que l'objet reste équipé) : on
    // recalcule/rafraîchit à chaque equip/unequip plutôt que de s'appuyer sur un
    // minuteur. Les actions des ItemExpectation non remplies sont fusionnées en un
    // seul ActiveEffect (id fixe) plutôt qu'un effet par objet.
    public void recomputeGradePenalty() {
        List<ItemExpectation.ExpectationEffect> unmetActions = getEquippedItems().stream().map(Item::getTemplate)
                .filter(EquipmentItem.class::isInstance).map(EquipmentItem.class::cast)
                .map(EquipmentItem::getExpectation).filter(Objects::nonNull)
                .filter(expectation -> !isSatisfied(expectation)).flatMap(expectation -> expectation.actions().stream())
                .toList();

        if (unmetActions.isEmpty()) {
            character.getEffectsSystem().remove(GRADE_PENALTY_EFFECT_ID);
            return;
        }

        List<StatModifier> modifiers = unmetActions.stream().flatMap(action -> action.modifiers().stream()).toList();
        Duration duration = unmetActions.stream().map(ItemExpectation.ExpectationEffect::duration)
                .max(Duration::compareTo).orElse(Duration.ZERO);
        character.getEffectsSystem().apply(new ActiveEffect(GRADE_PENALTY_EFFECT_ID, unmetActions.get(0).name(),
                modifiers, Instant.now().plus(duration)));
    }

    private boolean isSatisfied(ItemExpectation expectation) {
        return expectation.conditions().stream().allMatch(
                condition -> character.getSkillSystem().passiveLevelOf(condition.skillId()) >= condition.level());
    }
}

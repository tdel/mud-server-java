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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import app.domain.ActiveEffect;
import app.domain.SkillElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.ClassBaseStats;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterEffectExpired;
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
import app.domain.item.GradePenalty;
import app.domain.item.Item;
import app.domain.item.ItemExpectation;
import app.domain.item.ItemGrade;
import app.domain.item.ItemSet;
import app.domain.item.ItemType;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.CharacterAppearanceChanged;
import app.network.message.ingame.EquipmentView;
import app.network.message.ingame.GoldLooted;
import app.network.message.ingame.GoldSpent;
import app.network.message.ingame.ShotUsed;
import app.network.message.ingame.SoulshotUsed;
import app.network.message.ingame.SpiritshotUsed;

public final class InventorySystem {

    private final AbstractCharacter character;
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final Map<UUID, Instant> nextItemUseAt = new ConcurrentHashMap<>();
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

    // Un soulshot/spiritshot (pile illimitée, cf. ItemType.maxStack) n'a qu'une
    // seule pile par type+grade dans l'inventaire — jamais équipé, donc
    // getSlot() == null suffit à identifier la pile.
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
        for (StoredStack stored : store(item)) {
            DomainEventPublisher.publish(new CharacterLootedItem(player, stored.stack(), stored.merged()));
        }
    }

    public boolean buyItem(Item item, int price) {
        // Seuls les joueurs achètent : buyItem n'est appelé que sur des PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        if (!trySpendGold(price)) {
            return false;
        }
        character.send(new GoldSpent(price));
        DomainEventPublisher.publish(new CharacterSpentGold(player, price));
        // Le prix est réparti au prorata entre les piles touchées (un achat de 250
        // potions peut compléter une pile existante puis en créer plusieurs).
        int purchased = item.getQuantity();
        for (StoredStack stored : store(item)) {
            int stackPrice = (int) ((long) price * stored.added() / purchased);
            DomainEventPublisher
                    .publish(new ItemPurchased(player, stored.stack(), stackPrice, stored.added(), stored.merged()));
        }
        return true;
    }

    // Range un item fraîchement acquis (loot/achat) : un objet non stackable est
    // ajouté tel quel ; un objet stackable complète d'abord les piles non pleines
    // du même template, puis le reliquat forme de nouvelles piles de
    // ItemType.maxStack au plus (la première réutilise `item` lui-même).
    // Retourne chaque pile touchée, merged=true pour une pile déjà existante.
    private List<StoredStack> store(Item item) {
        List<StoredStack> stored = new ArrayList<>();
        if (!item.getType().stackable()) {
            item.setCharacter(character);
            addItem(item);
            stored.add(new StoredStack(item, 1, false));
            return stored;
        }
        int maxStack = item.getType().maxStack();
        int remaining = item.getQuantity();
        for (Item existing : items) {
            if (remaining == 0) {
                break;
            }
            if (existing.getSlot() != null || !existing.getTemplateId().equals(item.getTemplateId())
                    || existing.getQuantity() >= maxStack) {
                continue;
            }
            int added = Math.min(remaining, maxStack - existing.getQuantity());
            existing.setQuantity(existing.getQuantity() + added);
            remaining -= added;
            stored.add(new StoredStack(existing, added, true));
        }
        Item next = item;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            if (next == null) {
                next = new Item(UUID.randomUUID(), item.getTemplate(), character, null, 0, size);
            }
            next.setCharacter(character);
            next.setQuantity(size);
            addItem(next);
            stored.add(new StoredStack(next, size, false));
            remaining -= size;
            next = null;
        }
        return stored;
    }

    private record StoredStack(Item stack, int added, boolean merged) {
    }

    // Délai de réutilisation par template (toutes les piles d'un même objet le
    // partagent), cf. ConsumableItem.getReuseDelayMs. En mémoire seulement : il
    // ne survit pas à une reconnexion.
    public boolean isItemReady(UUID templateId) {
        return !Instant.now().isBefore(nextItemUseAt.getOrDefault(templateId, Instant.MIN));
    }

    public Duration remainingItemCooldown(UUID templateId) {
        Duration remaining = Duration.between(Instant.now(), nextItemUseAt.getOrDefault(templateId, Instant.MIN));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void markItemCooldown(UUID templateId, long delayMs) {
        if (delayMs > 0) {
            nextItemUseAt.put(templateId, Instant.now().plusMillis(delayMs));
        }
    }

    // Retire un exemplaire d'une pile (potion bue...) et retourne la quantité
    // restante — 0 si la pile a disparu de l'inventaire.
    public int consumeOne(Item item) {
        int remaining = item.getQuantity() - 1;
        if (remaining <= 0) {
            removeItem(item);
            return 0;
        }
        item.setQuantity(remaining);
        return remaining;
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

        // Arme à deux mains tenue : la main secondaire est condamnée (refus, cf.
        // isOffHandBlocked). À l'inverse, équiper une arme à deux mains libère la main
        // secondaire, retirée avec l'ancienne arme dans le même GamePlayerEquippedItem.
        if (slot == EquipmentSlot.OFF_HAND && isOffHandBlocked()) {
            return Optional.empty();
        }
        boolean freesOffHand = slot == EquipmentSlot.WEAPON && isTwoHanded(item);
        // Armure complète (robe/armure d'une pièce L2) : elle couvre les jambes, les
        // jambières portées sont retirées ; à l'inverse, enfiler des jambières retire
        // l'armure complète, comme dans L2.
        boolean freesLegs = slot == EquipmentSlot.CHEST && item.isFullArmor();
        boolean freesFullArmor = slot == EquipmentSlot.LEGS;

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : equipped) {
            if (!existing.getId().equals(item.getId())
                    && (existing.getSlot() == slot || freesOffHand && existing.getSlot() == EquipmentSlot.OFF_HAND
                            || freesLegs && existing.getSlot() == EquipmentSlot.LEGS
                            || freesFullArmor && existing.getSlot() == EquipmentSlot.CHEST && existing.isFullArmor())) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot);
        DomainEventPublisher.publish(new GamePlayerEquippedItem(player, item, slot, previousOccupants));
        broadcastAppearance(player);
        refreshGradePenalties();
        character.getStatSystem().recomputeStats(character.getAttributeSystem().getAttributes(),
                character.getLevelingSystem().getLevel(), this,
                player.getClassSystem().getCharacterClass().baseStats());
        return Optional.of(slot);
    }

    public void unequipItem(Item item) {
        // Seuls les joueurs déséquipent des objets : unequipItem n'est appelé que sur
        // des PlayerInstance.
        PlayerInstance player = (PlayerInstance) character;
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(player, item));
        broadcastAppearance(player);
        refreshGradePenalties();
        character.getStatSystem().recomputeStats(character.getAttributeSystem().getAttributes(),
                character.getLevelingSystem().getLevel(), this,
                player.getClassSystem().getCharacterClass().baseStats());
    }

    // Vrai tant qu'une arme à deux mains (WeaponType.twoHanded) est équipée : rien
    // ne peut alors occuper EquipmentSlot.OFF_HAND.
    public boolean isOffHandBlocked() {
        return getEquippedWeapon().filter(InventorySystem::isTwoHanded).isPresent();
    }

    // Répare au chargement un personnage sauvegardé avant cette règle (arme à deux
    // mains + bouclier) : la main secondaire est retirée comme par un unequip.
    public void releaseBlockedOffHand() {
        if (!isOffHandBlocked()) {
            return;
        }
        getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.OFF_HAND).forEach(this::unequipItem);
    }

    private static boolean isTwoHanded(Item item) {
        return item.getType() == ItemType.WEAPON && item.getWeaponType() != null && item.getWeaponType().twoHanded();
    }

    // Les autres joueurs à portée rhabillent ce personnage (le porteur, lui, a
    // déjà ItemEquipped/ItemUnequipped puis redemande son Inventory).
    private void broadcastAppearance(PlayerInstance player) {
        player.broadcast(new CharacterAppearanceChanged(player.getId(), player.getName(), EquipmentView.listOf(player)),
                player);
    }

    // Stats de base d'un joueur selon les fonctions L2 (CombatFormulas.stats) :
    // arme en main (p.atk, m.atk, vitesse, critique et précision de son type), à
    // défaut le poing de la classe ; p.def = pièces portées + défense nue des
    // emplacements vides (une armure complète couvre aussi les jambes) ; m.def =
    // bijoux portés + défense nue des emplacements de bijou vides. Le bouclier
    // n'ajoute pas de p.def : sa défense ne compte qu'en cas de blocage
    // (DamageModifiers.defenseWithBlock), il retire 8 d'esquive. MAX_MP porte le
    // bonus de PM des robes et bijoux (ajouté par ResourceSystem.getMaxMana).
    // Appelé à la construction de PlayerInstance (avant que son StatSystem existe)
    // puis par StatSystem.recomputeStats.
    public static Map<ModifiedStat, Integer> computeBaseStats(Map<Attribute, Integer> attributes, int level,
            List<Item> items, ClassBaseStats classBase) {
        List<Item> equipped = items.stream().filter(item -> item.getSlot() != null).toList();
        Optional<Item> weapon = equipped.stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();

        double pDef = classBase.totalBarePDef();
        double mDef = classBase.totalBareMDef();
        double accuracyBonus = 0;
        double evasionBonus = 0;
        int critBonus = 0;
        int maxMp = 0;
        for (Item item : equipped) {
            EquipmentSlot slot = item.getSlot();
            if (slot != EquipmentSlot.WEAPON && slot != EquipmentSlot.OFF_HAND) {
                pDef += item.getPDef() - classBase.barePDef(slot);
                if (slot == EquipmentSlot.CHEST && item.isFullArmor()) {
                    pDef -= classBase.barePDef(EquipmentSlot.LEGS);
                }
            }
            mDef += item.getMDef() - classBase.bareMDef(slot);
            accuracyBonus += item.getAccuracyBonus();
            evasionBonus += item.getEvasionBonus();
            if (slot != EquipmentSlot.WEAPON) {
                critBonus += item.getCritBonus();
            }
            maxMp += item.getMaxMp();
        }

        CombatFormulas.BaseValues base = new CombatFormulas.BaseValues(
                weapon.map(Item::getPAtk).map(Double::valueOf).orElse(classBase.pAtk()),
                weapon.map(Item::getMAtk).map(Double::valueOf).orElse(classBase.mAtk()), pDef, mDef, accuracyBonus,
                evasionBonus, weapon.map(Item::getCritBonus).map(Double::valueOf).orElse(classBase.critRate()),
                critBonus, weapon.map(Item::getAtkSpd).orElse(classBase.atkSpd()), classBase.runSpeed(),
                weapon.isPresent(), true);
        Map<ModifiedStat, Integer> stats = CombatFormulas.stats(base, attributes, level);
        stats.put(ModifiedStat.MAX_MP, maxMp);
        return stats;
    }

    public Map<ModifiedStat, Integer> computeSetBonuses() {
        Map<String, Long> equippedCountBySetId = getEquippedItems().stream().map(Item::getSetId)
                .filter(Objects::nonNull).collect(Collectors.groupingBy(setId -> setId, Collectors.counting()));

        Map<ModifiedStat, Integer> modifiers = new EnumMap<>(ModifiedStat.class);
        for (Map.Entry<String, Long> entry : equippedCountBySetId.entrySet()) {
            // Set sans bonus défini dans item_sets.xml (novice-set, doom-set...) : ignoré.
            // getById levait ici une IllegalStateException qui faisait échouer le
            // chargement du personnage (login) dès qu'une pièce d'un tel set était équipée.
            Optional<ItemSet> found = ItemSetCatalogHolder.findById(entry.getKey());
            if (found.isEmpty()) {
                continue;
            }
            ItemSet set = found.get();
            int piecesEquipped = entry.getValue().intValue();
            for (Map.Entry<Integer, Map<ModifiedStat, Integer>> tier : set.bonusByPieceCount().entrySet()) {
                if (piecesEquipped >= tier.getKey()) {
                    tier.getValue().forEach((stat, amount) -> modifiers.merge(stat, amount, Integer::sum));
                }
            }
        }
        return modifiers;
    }

    // Pénalités de grade (GradePenalty) : un objet équipé dont l'ItemExpectation
    // (cf. EquipmentItem.getExpectation) n'est pas remplie — un grade au-delà de
    // l'expertise débloquée — pose la pénalité d'arme (slot WEAPON) ou d'armure
    // (tout le reste : armures, bouclier, bijoux), au niveau de l'écart de grade
    // le plus grand. Pas de minuteur : on recalcule à chaque equip/unequip et à
    // chaque compétence passive apprise (Expertise Grade).
    //
    // Chargement du personnage (PlayerInstance) : posées en silence, le client
    // les reçoit avec l'état initial (GamePlayerStats/EntityView.effects).
    public void initGradePenalties() {
        updateGradePenalties(false);
    }

    // Changement en jeu : chaque pénalité posée, changée de niveau ou levée est
    // publiée comme un effet (CharacterEffectApplied/Expired) — icône chez tous
    // ceux qui voient le porteur, son groupe, et ses stats renvoyées.
    public void refreshGradePenalties() {
        updateGradePenalties(true);
    }

    private void updateGradePenalties(boolean announce) {
        ItemGrade expertise = character.getSkillSystem().unlockedGrade();
        int weaponLevel = 0;
        int armorLevel = 0;
        for (Item item : getEquippedItems()) {
            if (!(item.getTemplate() instanceof EquipmentItem equipment) || equipment.getExpectation() == null
                    || isSatisfied(equipment.getExpectation())) {
                continue;
            }
            int level = Math.max(1, GradePenalty.levelFor(item.getGrade(), expertise));
            if (item.getSlot() == EquipmentSlot.WEAPON) {
                weaponLevel = Math.max(weaponLevel, level);
            } else {
                armorLevel = Math.max(armorLevel, level);
            }
        }
        updateGradePenalty(GradePenalty.WEAPON_EFFECT_ID, GradePenalty.weapon(weaponLevel), announce);
        updateGradePenalty(GradePenalty.ARMOR_EFFECT_ID, GradePenalty.armor(armorLevel), announce);
    }

    private void updateGradePenalty(UUID effectId, Optional<ActiveEffect> wanted, boolean announce) {
        EffectsSystem effects = character.getEffectsSystem();
        Optional<ActiveEffect> current = effects.find(effectId);
        if (wanted.isEmpty()) {
            if (current.isPresent()) {
                effects.remove(effectId);
                if (announce) {
                    DomainEventPublisher.publish(new CharacterEffectExpired(character, current.get()));
                }
            }
            return;
        }
        if (current.equals(wanted)) {
            return;
        }
        effects.apply(wanted.get());
        if (announce) {
            DomainEventPublisher.publish(new CharacterEffectApplied(character, wanted.get()));
        }
    }

    private boolean isSatisfied(ItemExpectation expectation) {
        return expectation.conditions().stream().allMatch(
                condition -> character.getSkillSystem().passiveLevelOf(condition.skillId()) >= condition.level());
    }
}

package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemPickedUp;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.domain.WeaponCategory;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

/**
 * {@code connection} n'est jamais persisté ni pris en compte par
 * {@link #equals}/{@link #hashCode} : il ne représente rien en base, seulement
 * l'état vivant du personnage tant qu'il est en jeu (voir
 * {@code GameWorld.enterWorld}) — même convention pour {@code currentRoom},
 * porté par {@link GameCharacter}. Un personnage fraîchement chargé depuis
 * {@code CharacterDao} n'a ni connexion ni room courante tant qu'il n'a pas
 * rejoint le monde via {@link #spawnToRoom} ou {@link #moveToRoom}.
 */
public final class GamePlayer extends GameCharacter {

    private UUID accountId;
    private UUID currentRoomId;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;
    private Attribute primaryAbility;
    private final Set<Attribute> savingThrowProficiencies;
    private final Set<Skill> skillProficiencies;
    private final Set<WeaponCategory> weaponProficiencies;
    private final Set<ArmorProficiency> armorProficiencies;
    private int level;

    private Connection connection;
    private final PlayerInventory inventory;
    private GameMonster target;
    private int xp;

    public GamePlayer(UUID id, UUID accountId, String name, UUID currentRoomId, Gender gender, Race race,
            CharacterClass characterClass, Attribute primaryAbility, Set<Attribute> savingThrowProficiencies,
            Set<Skill> skillProficiencies, Set<WeaponCategory> weaponProficiencies,
            Set<ArmorProficiency> armorProficiencies, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.accountId = accountId;
        this.currentRoomId = currentRoomId;
        this.gender = gender;
        this.race = race;
        this.characterClass = characterClass;
        this.primaryAbility = primaryAbility;
        this.savingThrowProficiencies = Set.copyOf(savingThrowProficiencies);
        this.skillProficiencies = Set.copyOf(skillProficiencies);
        this.weaponProficiencies = Set.copyOf(weaponProficiencies);
        this.armorProficiencies = Set.copyOf(armorProficiencies);
        this.level = level;
        this.xp = xp;
        this.inventory = new PlayerInventory(gold);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(UUID currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public CharacterClass getCharacterClass() {
        return characterClass;
    }

    public void setCharacterClass(CharacterClass characterClass) {
        this.characterClass = characterClass;
    }

    public Attribute getPrimaryAbility() {
        return primaryAbility;
    }

    public Set<Attribute> getSavingThrowProficiencies() {
        return savingThrowProficiencies;
    }

    public Set<Skill> getSkillProficiencies() {
        return skillProficiencies;
    }

    public Set<WeaponCategory> getWeaponProficiencies() {
        return weaponProficiencies;
    }

    public Set<ArmorProficiency> getArmorProficiencies() {
        return armorProficiencies;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
    }

    @Override
    public int getArmorClass() {
        int ac = inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(this::armorAc).orElseGet(super::getArmorClass);

        return ac + inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.OFF_HAND)
                .mapToInt(item -> item.getBaseAc() + item.getBonus()).sum();
    }

    private int armorAc(Item armor) {
        int dexMod = getModifier(Attribute.DEXTERITY);
        int baseAndBonus = armor.getBaseAc() + armor.getBonus();
        return switch (armor.getArmorCategory()) {
            case LIGHT -> baseAndBonus + dexMod;
            case MEDIUM -> baseAndBonus + Math.min(dexMod, 2);
            case HEAVY -> baseAndBonus;
        };
    }

    /**
     * Vrai si un item actuellement équipé exige une {@link ArmorProficiency} que ce
     * personnage n'a pas — granularité "toute pièce non maîtrisée déclenche le
     * désavantage", cohérente avec le fait que ce jeu modélise déjà l'armure en
     * plusieurs emplacements indépendants plutôt qu'une seule "armure portée" comme
     * en RAW strict. Consommé par {@code game.dice.DiceRoller}/
     * {@code game.CombatService} pour appliquer le désavantage SRD (jets de
     * FOR/DEX, jets d'attaque) plutôt que de bloquer l'équipement lui-même —
     * {@link #equipItem} ne fait aucune vérification de maîtrise.
     */
    public boolean isWearingNonProficientArmor() {
        return inventory.getEquippedItems().stream().map(this::requiredArmorProficiency)
                .anyMatch(required -> required.isPresent() && !armorProficiencies.contains(required.get()));
    }

    private Optional<ArmorProficiency> requiredArmorProficiency(Item item) {
        return switch (item.getType()) {
            case ARMOR, HELMET, PANTS, BOOTS, GLOVES -> Optional.of(ArmorProficiency.of(item.getArmorCategory()));
            case SHIELD -> Optional.of(ArmorProficiency.SHIELDS);
            default -> Optional.empty();
        };
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public GameMonster getTarget() {
        return target;
    }

    public void setTarget(GameMonster target) {
        this.target = target;
    }

    public int getXp() {
        return xp;
    }

    /**
     * Seul point d'entrée pour muter l'XP — pas de setter public, sur le même
     * principe que {@link #pickUpItem}/{@link #equipItem} : la mutation publie
     * toujours {@link CharacterGainedXp}, dont le listener (voir
     * {@code game.actor.CharacterService}) décide d'un éventuel passage de niveau,
     * hors de portée d'un simple POJO sans accès à {@code LevelService}/
     * {@code ClassService}.
     */
    public void gainXp(int amount) {
        this.xp += amount;
        DomainEventPublisher.publish(new CharacterGainedXp(this, amount));
    }

    /**
     * Même principe que {@link #gainXp} : mute l'état en mémoire puis publie
     * {@link CharacterReceivedGold}, dont le listener ({@code game.actor
     * .CharacterService}) persiste et envoie le message au joueur — un simple POJO
     * n'a pas accès à {@code CharacterDao}. Appelé depuis {@code game.actor
     * .LootService} sur la mort d'un monstre ; aucun verrou nécessaire, même
     * raisonnement que {@link #equipItem}/{@link #unequipItem} : un joueur ne mute
     * jamais son propre inventaire que depuis le thread virtuel unique de sa propre
     * connexion (ici, celui qui exécute la commande {@code attack} portant le coup
     * fatal).
     */
    public void receiveGold(int amount) {
        inventory.addGold(amount);
        DomainEventPublisher.publish(new CharacterReceivedGold(this, amount));
    }

    /**
     * Contrairement à {@link #pickUpItem}, {@code item} n'a jamais existé en room
     * ni en base — c'est un objet fraîchement créé par {@code game.actor
     * .LootService} à partir d'une table de butin. Pas de disputé possible entre
     * joueurs (personne d'autre ne détient de référence vers cette instance avant
     * cet appel), donc pas de {@code synchronized} nécessaire ici, contrairement à
     * {@link #pickUpItem}. Publie {@link CharacterLootedItem}, dont le listener
     * ({@code game.ItemService}) attache le template et insère la ligne en base
     * (contrairement à {@code ItemPickedUp}, qui ne fait que réassigner une ligne
     * déjà existante).
     */
    public void receiveLootItem(Item item) {
        item.setCharacter(this);
        inventory.addItem(item);
        DomainEventPublisher.publish(new CharacterLootedItem(this, item));
    }

    /**
     * Même principe que {@link #receiveGold}/{@link #receiveLootItem}, combinés :
     * débite {@code price} avant d'attacher {@code item}, contrairement à un butin
     * qui ne peut pas échouer. {@code item} est construit par l'appelant
     * ({@code controller.ingame.Talk}) exactement comme {@code game.actor
     * .LootService} construit un item de butin ({@code new Item(UUID.randomUUID(),
     * templateId, null, getId(), null)}). Publie {@link CharacterSpentGold} puis
     * {@link ItemPurchased} — deux événements distincts plutôt qu'un seul combiné,
     * chacun écouté par le service propriétaire de sa donnée
     * ({@code game.actor.CharacterService} pour l'or, {@code game.ItemService} pour
     * l'item), même séparation que {@code game.actor.LootService
     * .onCharacterDied} qui appelle {@link #receiveGold} et
     * {@link #receiveLootItem} séparément.
     *
     * @return true si l'achat a réussi (or suffisant), false sinon — aucune
     *         mutation n'a lieu dans ce cas
     */
    public boolean buyItem(Item item, int price) {
        if (!inventory.trySpendGold(price)) {
            return false;
        }
        DomainEventPublisher.publish(new CharacterSpentGold(this, price));
        item.setCharacter(this);
        inventory.addItem(item);
        DomainEventPublisher.publish(new ItemPurchased(this, item, price));
        return true;
    }

    /**
     * Contrairement à {@link GameMonster#takeDamage}, aucun verrou propre n'est
     * nécessaire ici : un joueur n'appartient jamais qu'à un seul
     * {@link CombatEncounter} à la fois (la règle de fusion de
     * {@code game.CombatEngine} refuse un second affrontement concurrent), et toute
     * mutation de PV liée au combat n'a lieu que pendant qu'un thread détient déjà
     * le verrou de cet affrontement (son propre tour, ou la riposte d'un monstre
     * pendant la cascade) — la sérialisation est donc héritée de ce verrou-là,
     * transitivement, plutôt que portée par cette méthode elle-même. Publie
     * {@link GamePlayerDied} sur le coup fatal, pendant côté joueur de
     * {@link GameMonster#takeDamage}.
     *
     * @return true si ce coup est celui qui a fait passer les PV à 0 ou moins
     */
    public boolean takeDamage(int amount, GameMonster attacker) {
        if (getCurrentHealth() <= 0) {
            return false;
        }
        setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
        boolean defeated = getCurrentHealth() <= 0;
        if (defeated) {
            DomainEventPublisher.publish(new GamePlayerDied(this, attacker));
        }
        return defeated;
    }

    /**
     * Précondition : le personnage est déjà dans le monde, donc {@code currentRoom}
     * est déjà renseigné (voir {@link #spawnToRoom} pour l'entrée initiale, qui n'a
     * pas de room d'origine). Place le personnage sur la case de spawn de
     * {@code destination} — utilisé par la restauration après la mort
     * ({@code CharacterService#onGamePlayerDied}), où il n'y a pas de case cible de
     * portail à respecter.
     */
    public void moveToRoom(Room destination) {
        moveToRoom(destination, destination.getSpawnCell());
    }

    /**
     * Variante utilisée par {@link #crossPortal} lorsqu'un déplacement case par
     * case fait franchir un {@code RoomPortal} : {@code targetCell} est la case
     * cible du portail plutôt que la case de spawn de la room.
     */
    public void moveToRoom(Room destination, HexCoordinate targetCell) {
        Room previous = getCurrentRoom();
        previous.leave(this);
        destination.join(this, targetCell);
        DomainEventPublisher.publish(new GamePlayerMovedToRoom(this, previous, destination));
    }

    /**
     * Seul sous-type à réellement traverser un {@link RoomPortal} rencontré par
     * {@link #moveToCell} : {@link GameMonster}/{@link GameNpc} restent sur la
     * case-portail (voir la base {@code GameCharacter#crossPortal}).
     */
    @Override
    protected boolean crossPortal(RoomPortal portal) {
        moveToRoom(portal.targetRoom(), portal.targetCell());
        return true;
    }

    /**
     * Seul sous-type à publier {@link GamePlayerEnteredCell} : {@link GameMonster}/
     * {@link GameNpc} restent la source, jamais la cible, d'une zone de présence
     * (aucun n'a d'IA de déplacement à ce jour). Le garde {@link #isInCombat()} en
     * tête évite de republier l'événement à chaque commande {@code go} tant que le
     * joueur reste dans une zone déjà engagée — {@code game.CombatEngine} l'écoute
     * de façon synchrone (pas de {@code @Async} dans le projet), donc au retour de
     * {@code publish}, l'affrontement éventuel a déjà été résolu jusqu'au tour
     * suivant du joueur.
     */
    @Override
    protected boolean onEnteredCell(HexCoordinate cell) {
        if (isInCombat()) {
            return false;
        }
        DomainEventPublisher.publish(new GamePlayerEnteredCell(this, cell));
        return isInCombat();
    }

    public void spawnToRoom(Room room) {
        room.join(this);
        DomainEventPublisher.publish(new GamePlayerSpawnedToRoom(this, room));
    }

    /**
     * Précondition : {@code item.getRoom()} désigne {@code currentRoom} — garanti
     * par {@link Room#findOneByName}, seul point d'entrée du ramassage, et par le
     * fait que tout personnage capable d'atteindre l'état {@code INGAME} a déjà
     * traversé {@link #spawnToRoom} (à la création ou à l'entrée en jeu). Suppose
     * aussi qu'il n'existe jamais qu'une seule instance JVM vivante de {@code item}
     * (cache chaud de {@code RoomService}/ {@code ItemService}, jamais rechargé par
     * requête) — sinon {@code synchronized(item)} ne synchroniserait rien entre
     * deux appels concurrents portant sur des instances différentes du même item.
     *
     * <p>
     * Deux joueurs peuvent réellement se disputer un item non possédé sous les
     * virtual threads — le verrou porte sur l'instance {@code Item} elle-même
     * plutôt que sur une ligne DB (remplace l'ancien {@code SELECT ... FOR UPDATE}
     * de {@code ItemDao#findByIdForUpdate}), la gestion des items étant désormais
     * entièrement en mémoire, la DB n'étant qu'une projection mise à jour après
     * coup via l'événement {@link ItemPickedUp}. {@code synchronized} ne bloque
     * plus (« pin ») les virtual threads sur leur carrier depuis JEP 491 (JDK 24+),
     * donc ce verrou respecte la contrainte de
     * {@code config.VirtualThreadExecutorConfig}. Le retrait de {@code currentRoom}
     * vit aussi dans le bloc verrouillé : toute la transition de possesseur (item
     * quitte la room, rejoint le personnage gagnant) reste une unité atomique face
     * à un autre {@code pickUpItem} concurrent sur le même item.
     *
     * @return true si {@code this} porte désormais l'item, false si un autre
     *         personnage l'a pris entre-temps
     */
    public boolean pickUpItem(Item item) {
        synchronized (item) {
            if (item.getCharacter() != null) {
                return false;
            }
            item.setCharacter(this);
            getCurrentRoom().removeItem(item);
        }
        inventory.addItem(item);
        DomainEventPublisher.publish(new ItemPickedUp(this, item));
        return true;
    }

    /**
     * Aucun verrou nécessaire ici (contrairement à {@link #pickUpItem}) : un
     * personnage n'est piloté que par sa propre connexion, dont les commandes sont
     * traitées une par une, dans l'ordre, par un unique virtual thread (voir
     * {@code telnet.TelnetSessionHandler}) — deux threads ne peuvent donc jamais
     * muter l'inventaire du même personnage en même temps. Ce raisonnement suppose
     * qu'un personnage n'est jamais piloté que par une seule connexion à la fois,
     * ce qui n'est pas encore garanti pour de vrai — voir le TODO dans
     * {@code controller.connected.Login}.
     *
     * <p>
     * Publie un seul événement portant à la fois l'item équipé et l'éventuel
     * occupant précédent du même emplacement, pour que le listener persiste les
     * deux dans une même transaction — la contrainte différée
     * {@code uniq_character_slot} (V1__init_schema.sql) protège le chevauchement
     * transitoire entre les deux UPDATE au sein de cette transaction.
     */
    public Optional<EquipmentSlot> equipItem(Item item) {
        Optional<EquipmentSlot> slot = item.getType().equipmentSlot();

        if (slot.isEmpty()) {
            return Optional.empty();
        }

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : inventory.getEquippedItems()) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot.get()) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot.get());
        DomainEventPublisher.publish(new GamePlayerEquippedItem(this, item, slot.get(), previousOccupants));
        return slot;
    }

    public void unequipItem(Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(this, item));
    }

    /**
     * Aucun verrou nécessaire (même raisonnement que {@link #equipItem}/
     * {@link #unequipItem}, voir leur Javadoc) : un personnage n'est piloté que par
     * sa propre connexion, dont les commandes sont traitées une par une par un
     * unique virtual thread. Précondition : {@code item} fait partie de
     * l'inventaire de {@code this} — garanti par {@link #findOneByName}, seul point
     * d'entrée du drop.
     */
    public void dropItem(Item item) {
        Room currentRoom = getCurrentRoom();
        item.setRoom(currentRoom);
        inventory.removeItem(item);
        currentRoom.addItem(item);
        DomainEventPublisher.publish(new GamePlayerDroppedItem(this, item, currentRoom));
    }

    public PlayerInventory getInventory() {
        return inventory;
    }

    public void send(OutputMessage message) {
        if (null != connection) {
            this.connection.send(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GamePlayer other)) {
            return false;
        }
        return level == other.level && xp == other.xp && inventory.getGold() == other.inventory.getGold()
                && getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && Objects.equals(getId(), other.getId()) && Objects.equals(accountId, other.accountId)
                && Objects.equals(getName(), other.getName()) && Objects.equals(currentRoomId, other.currentRoomId)
                && gender == other.gender && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), accountId, getName(), currentRoomId, gender, race, characterClass, level, xp,
                inventory.getGold(), getCurrentHealth(), getMaxHealth(), getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + accountId + ", name=" + getName() + ", currentRoomId="
                + currentRoomId + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventory.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", attributes=" + getAttributes() + "]";
    }
}

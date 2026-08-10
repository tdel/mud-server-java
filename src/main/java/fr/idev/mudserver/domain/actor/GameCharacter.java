package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.HexDirection;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomPortal;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;

/**
 * Racine commune à tout ce qui porte des caractéristiques DnD5e et une santé,
 * et peut occuper une {@link Room} : {@link GamePlayer}, {@link GameMonster} et
 * {@link GameNpc}. {@code currentRoom} n'est jamais persisté ni pris en compte
 * par les {@code equals}/{@code hashCode} des sous-classes concrètes — il ne
 * représente que l'état vivant du process, sur le même principe que
 * {@code GamePlayer.connection} (voir sa Javadoc). {@code permits} scelle la
 * hiérarchie à ces trois sous-types : {@code Room.findOccupantByName} peut
 * ainsi retourner un {@code Optional<GameCharacter>} traité par un
 * {@code switch} exhaustif dans {@code Examine}, sans clause {@code default}.
 *
 * <p>
 * {@code GameNpc} hérite {@code attributes}/{@code currentHealth}/
 * {@code maxHealth} sans qu'aucune règle ne les exploite encore — un NPC reste
 * pour l'instant juste un nom et une localisation (voir sa Javadoc).
 *
 * <p>
 * {@code encounter} porte la même sémantique « état vivant du process, jamais
 * persisté » que {@code currentRoom} ci-dessous, mais est {@code volatile}
 * plutôt qu'un simple champ : contrairement à
 * {@code currentRoom}/{@code GamePlayer.connection}/{@code GamePlayer.target},
 * qui ne sont jamais mutés que par le thread de la connexion du personnage
 * lui-même, une cascade de {@code game.CombatEngine} peut réassigner
 * l'{@code encounter} d'un <em>autre</em> participant (celui qui vient de
 * mourir ou d'être retiré) depuis le thread d'un troisième personnage —
 * {@code volatile} garantit la visibilité immédiate de cette réassignation aux
 * lectures simples ({@link #isInCombat()}) qui n'ont pas besoin d'un verrou
 * complet sur {@link CombatEncounter}.
 *
 * <p>
 * {@code position}/{@code speed} suivent la même convention "état vivant du
 * process, jamais persisté" que {@code currentRoom} : la case exacte occupée
 * dans la grille hexagonale de {@code currentRoom} (voir {@code Room.width}/
 * {@code Room.height}) ne survit ni à une déconnexion ni à un redémarrage — le
 * personnage réapparaît sur la case de spawn de la room (ou la case cible d'un
 * portail) à chaque {@code join}. {@code speed} borne le nombre de cases
 * franchissables par une commande {@code go} (voir {@link #moveToCell}) ; la
 * valeur par défaut (6) reprend par analogie la vitesse de marche 5e standard
 * (30 ft ≈ 6 cases de 5 ft), cohérente avec le reste du projet qui émule les
 * règles 5e ailleurs (initiative, jets de sauvegarde).
 *
 * <p>
 * {@link #moveToCell} est partagé par les trois sous-types : bornage par
 * vitesse, avance case par case et réclamation/libération atomique de case
 * (voir {@link Room#tryClaimCell}) ne dépendent d'aucun champ propre à
 * {@link GamePlayer}. Seul le franchissement d'un {@link RoomPortal} diffère
 * par sous-type, via le point d'extension {@link #crossPortal} : la base ne
 * fait rien (un monstre/PNJ s'arrête sur la case-portail sans changer de room),
 * {@link GamePlayer} redéfinit pour réellement traverser vers la room liée.
 *
 * <p>
 * {@code actionEconomy} suit la même convention « état vivant du process,
 * jamais persisté » que {@code currentRoom}/{@code position}/{@code speed}
 * ci-dessus : aucune fonctionnalité n'accorde encore d'action permanente
 * supplémentaire, donc rien ne justifie une colonne DB pour l'instant — le jour
 * où une telle fonctionnalité (feat, feature de classe) existera, elle devra
 * réappliquer son bonus au chargement du personnage plutôt que persister un
 * compteur brut, sur le même principe que la CA qui n'est jamais stockée
 * précalculée à partir de l'équipement.
 */
public abstract sealed class GameCharacter extends GameObject permits GamePlayer, GameMonster, GameNpc {

    public static final int DEFAULT_SPEED = 6;

    private final Map<Attribute, Integer> attributes;
    private int currentHealth;
    private int maxHealth;

    private Room currentRoom;
    private HexCoordinate position;
    protected int speed = DEFAULT_SPEED;
    private volatile CombatEncounter encounter;
    private final ActionEconomy actionEconomy = new ActionEconomy();

    protected GameCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth) {
        super(id, name);
        this.attributes = new EnumMap<>(attributes);
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public int getArmorClass() {
        return 10 + getModifier(Attribute.DEXTERITY);
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(Room currentRoom) {
        this.currentRoom = currentRoom;
    }

    public HexCoordinate getPosition() {
        return position;
    }

    public void setPosition(HexCoordinate position) {
        this.position = position;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public boolean isInCombat() {
        return encounter != null;
    }

    public CombatEncounter getEncounter() {
        return encounter;
    }

    public void setEncounter(CombatEncounter encounter) {
        this.encounter = encounter;
    }

    public ActionEconomy getActionEconomy() {
        return actionEconomy;
    }

    /**
     * Jet d'initiative DnD5e standard (1d20 + modificateur de DEX), commun aux
     * joueurs et aux monstres puisque tous deux sont des {@link GameCharacter}.
     * Consommé par
     * {@code game.CombatEngine}/{@link CombatEncounter#establishInitiativeOrder}.
     */
    public int rollInitiative() {
        return DiceRoller.roll(new DiceExpression(1, 20, getModifier(Attribute.DEXTERITY))).total();
    }

    /**
     * Avance d'une case à la fois vers {@code direction}, jusqu'à
     * {@code min(requestedCells, getSpeed())} : chaque pas réclame la case suivante
     * ({@link Room#tryClaimCell}) avant de libérer l'ancienne, jamais l'inverse —
     * un personnage ne perd ainsi jamais son propre pied d'appui face à un
     * concurrent visant la même case. Le déplacement intra-room ne touche jamais la
     * DB ni ne publie d'événement ; seul {@link #crossPortal} peut le faire pour le
     * sous-type qui le redéfinit. Atterrir sur un portail consomme le reste du
     * budget de déplacement : pas d'enchaînement dans la nouvelle room en une seule
     * commande {@code go} (simplification assumée).
     */
    public MovementOutcome moveToCell(HexDirection direction, int requestedCells) {
        int budget = Math.min(requestedCells, getSpeed());
        Room room = getCurrentRoom();
        HexCoordinate current = getPosition();

        int cellsMoved = 0;
        boolean blockedByOccupant = false;
        boolean crossedPortal = false;
        boolean triggeredCombat = false;

        for (int i = 0; i < budget; i++) {
            HexCoordinate next = current.neighbor(direction);
            if (!room.isInBounds(next)) {
                break;
            }
            if (!room.tryClaimCell(next, this)) {
                blockedByOccupant = true;
                break;
            }
            room.releaseCell(current, this);
            setPosition(next);
            current = next;
            cellsMoved++;

            if (onEnteredCell(current)) {
                triggeredCombat = true;
                break;
            }

            Optional<RoomPortal> portal = room.findPortalAt(current);
            if (portal.isPresent()) {
                crossedPortal = crossPortal(portal.get());
                break;
            }
        }

        boolean blockedByBounds = cellsMoved == 0 && !blockedByOccupant;
        return new MovementOutcome(cellsMoved, blockedByBounds, blockedByOccupant, crossedPortal, triggeredCombat);
    }

    /**
     * Point d'extension de {@link #moveToCell} : {@link GameMonster}/
     * {@link GameNpc} restent sur place (la boucle s'est déjà arrêtée sur la
     * case-portail), seul {@link GamePlayer} redéfinit pour réellement traverser
     * vers la room liée.
     *
     * @return true si {@code this} a effectivement changé de room
     */
    protected boolean crossPortal(RoomPortal portal) {
        return false;
    }

    /**
     * Point d'extension de {@link #moveToCell}, appelé à chaque case franchie
     * (avant le test de portail, voir sa Javadoc) : seul {@link GamePlayer}
     * redéfinit, pour détecter l'entrée dans la zone de présence d'un
     * {@link GameMonster} — un monstre/PNJ ne déclenche jamais de combat en se
     * déplaçant lui-même (aucun n'a d'IA de déplacement à ce jour).
     *
     * @return true si {@code this} vient d'entrer en combat, ce qui consomme le
     *         reste du budget de déplacement de cette commande, comme un portail
     */
    protected boolean onEnteredCell(HexCoordinate cell) {
        return false;
    }

    public record MovementOutcome(int cellsMoved, boolean blockedByBounds, boolean blockedByOccupant,
            boolean crossedPortal, boolean triggeredCombat) {
    }
}

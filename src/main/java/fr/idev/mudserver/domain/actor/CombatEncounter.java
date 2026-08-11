package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import fr.idev.mudserver.domain.RoomInstance;

/**
 * Affrontement partagé par plusieurs {@link GamePlayer}/{@link GameMonster} —
 * un même combat peut regrouper N joueurs et N monstres, contrairement au
 * modèle 1v1 initial. POJO pur sans dépendance Spring/{@code DiceRoller} (voir
 * {@link GamePlayer#tryAttack}/{@link GameMonster#tryAttack}, seuls
 * responsables des jets de dés) : toutes les méthodes ici reçoivent des valeurs
 * déjà tirées, ou une fonction de tirage à invoquer en interne (voir
 * {@link #establishInitiativeOrder}) — ça garde cette classe testable en
 * unitaire pur, comme {@code GamePlayerTest}/{@code GameMonsterTest}.
 *
 * <p>
 * Toutes les méthodes sont {@code synchronized} sur l'instance elle-même :
 * c'est le « verrou B » du schéma de verrouillage de {@code game.CombatEngine},
 * ré-entrant, jamais acquis alors qu'un verrou de {@link GameMonster} (« verrou
 * A », voir {@link GameMonster#takeDamage}) est déjà tenu — seul le sens
 * inverse (verrou B tenu, puis verrou A pris à l'intérieur via
 * {@code GameMonster#takeDamage}/ {@code GamePlayer#takeDamage} appelés depuis
 * la cascade de {@code CombatEngine}) existe dans ce code, ce qui exclut tout
 * interblocage.
 *
 * <p>
 * Point le plus sensible de la classe : l'arithmétique du pointeur de tour
 * ({@code currentTurnIndex}) lors d'une insertion ({@link #insertLatecomer}) ou
 * d'un retrait ({@link #remove}). Exemple : {@code order=[A,B,C]}, pointeur=1
 * (désigne B). Insertion de X à l'index 0 → pointeur devient 2 (désigne
 * toujours B). Retrait de B (index == pointeur) → C glisse à l'index 1,
 * pointeur reste 1 (désigne maintenant C, qui doit jouer ensuite —
 * {@link List#remove(int)} décale déjà les éléments suivants, donc ne rien
 * faire au pointeur suffit dans ce cas précis). Retrait d'un participant
 * strictement avant le pointeur → pointeur décrémenté. Retrait du dernier
 * élément de la liste alors qu'il était justement celui désigné par le pointeur
 * → pointeur revient à 0 (boucle sur le début du nouvel ordre).
 */
public final class CombatEncounter {

    private final RoomInstance room;
    private final List<GameCharacter> pendingJoiners = new ArrayList<>();
    private final List<InitiativeEntry> order = new ArrayList<>();
    private boolean initiativeRolled;
    private int currentTurnIndex = -1;

    private record InitiativeEntry(GameCharacter character, int initiative) {
    }

    public CombatEncounter(RoomInstance room) {
        this.room = room;
    }

    public RoomInstance getRoom() {
        return room;
    }

    /**
     * Rejoindre avant que l'initiative ne soit établie — simple mise en attente,
     * pas de jet ici (voir {@link #establishInitiativeOrder}, qui relit cette liste
     * sous verrou au moment du tirage, pour ne perdre aucun rejoignant concurrent).
     */
    public synchronized void joinBeforeInitiative(GameCharacter character) {
        if (initiativeRolled) {
            throw new IllegalStateException(
                    "Initiative déjà établie, " + character.getName() + " doit rejoindre via insertLatecomer");
        }
        pendingJoiners.add(character);
    }

    public synchronized boolean isInitiativeRolled() {
        return initiativeRolled;
    }

    /**
     * Tire l'initiative de chaque participant actuellement dans
     * {@code pendingJoiners} — lu ici, sous verrou, plutôt que dans une {@code Map}
     * précalculée par l'appelant, pour qu'un rejoignant concurrent arrivé juste
     * avant l'acquisition du verrou (via {@link #joinBeforeInitiative}) soit bien
     * inclus dans le tirage au lieu d'être silencieusement perdu.
     */
    public synchronized void establishInitiativeOrder(Function<GameCharacter, Integer> initiativeRoller) {
        for (GameCharacter character : pendingJoiners) {
            order.add(new InitiativeEntry(character, initiativeRoller.apply(character)));
        }
        pendingJoiners.clear();
        order.sort(this::compareEntries);
        initiativeRolled = true;
        currentTurnIndex = 0;
    }

    /**
     * Insertion triée d'un participant rejoignant un affrontement dont l'initiative
     * est déjà établie — règle 5e stricte (pas ajouté en fin de liste). Voir la
     * Javadoc de classe pour la correction du pointeur.
     */
    public synchronized void insertLatecomer(GameCharacter character, int initiative) {
        if (!initiativeRolled) {
            throw new IllegalStateException("L'initiative n'a pas encore été établie pour cet affrontement");
        }
        int insertionIndex = order.size();
        for (int i = 0; i < order.size(); i++) {
            InitiativeEntry existing = order.get(i);
            if (compareEntries(new InitiativeEntry(character, initiative), existing) < 0) {
                insertionIndex = i;
                break;
            }
        }
        order.add(insertionIndex, new InitiativeEntry(character, initiative));
        if (insertionIndex <= currentTurnIndex) {
            currentTurnIndex++;
        }
    }

    /**
     * Retrait d'un participant (mort, ou retiré autrement) — pas d'effet si
     * {@code character} n'est ni dans {@code pendingJoiners} ni dans {@code order}
     * (ex. déjà retiré). Voir la Javadoc de classe pour la correction du pointeur.
     */
    public synchronized void remove(GameCharacter character) {
        pendingJoiners.remove(character);

        int index = indexOf(character);
        if (index < 0) {
            return;
        }
        order.remove(index);
        if (index < currentTurnIndex) {
            currentTurnIndex--;
        } else if (currentTurnIndex >= order.size() && !order.isEmpty()) {
            currentTurnIndex = 0;
        }
    }

    public synchronized GameCharacter currentParticipant() {
        if (order.isEmpty() || currentTurnIndex < 0 || currentTurnIndex >= order.size()) {
            return null;
        }
        return order.get(currentTurnIndex).character();
    }

    public synchronized void advanceTurn() {
        if (order.isEmpty()) {
            return;
        }
        currentTurnIndex = (currentTurnIndex + 1) % order.size();
    }

    /**
     * Un affrontement est terminé s'il ne reste plus aucun monstre ou plus aucun
     * joueur dans l'ordre établi — {@code false} tant que l'initiative n'a pas
     * encore été tirée (le coup d'ouverture, hors ordre, ne peut pas à lui seul
     * clore l'affrontement autrement qu'en tuant le monstre — auquel cas
     * {@code CombatEngine} n'établit jamais l'ordre du tout).
     */
    public synchronized boolean isOver() {
        if (!initiativeRolled) {
            return false;
        }
        boolean hasMonster = order.stream().anyMatch(entry -> entry.character() instanceof GameMonster);
        boolean hasPlayer = order.stream().anyMatch(entry -> entry.character() instanceof GamePlayer);
        return !hasMonster || !hasPlayer;
    }

    public synchronized List<GameCharacter> participants() {
        return order.stream().map(InitiativeEntry::character).toList();
    }

    public synchronized List<GamePlayer> livingPlayers() {
        return order.stream().map(InitiativeEntry::character)
                .filter(character -> character instanceof GamePlayer && character.getCurrentHealth() > 0)
                .map(GamePlayer.class::cast).toList();
    }

    /**
     * Tri décroissant par initiative, départagé par modificateur de DEX décroissant
     * en cas d'égalité — évite de faire rejouer les dés pour un simple ex æquo.
     */
    private int compareEntries(InitiativeEntry a, InitiativeEntry b) {
        int byInitiative = -Integer.compare(a.initiative(), b.initiative());
        if (byInitiative != 0) {
            return byInitiative;
        }
        return -Integer.compare(a.character().getModifier(Attribute.DEXTERITY),
                b.character().getModifier(Attribute.DEXTERITY));
    }

    private int indexOf(GameCharacter character) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).character() == character) {
                return i;
            }
        }
        return -1;
    }
}

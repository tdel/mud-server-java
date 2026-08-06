package fr.idev.mudserver.game;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.CombatEncounter;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.message.ingame.AlreadyInAnotherEncounter;
import fr.idev.mudserver.network.message.ingame.AttackResult;
import fr.idev.mudserver.network.message.ingame.CombatantJoined;
import fr.idev.mudserver.network.message.ingame.EncounterEnded;
import fr.idev.mudserver.network.message.ingame.ItemUseNotImplemented;
import fr.idev.mudserver.network.message.ingame.ItemUseRequiresCombat;
import fr.idev.mudserver.network.message.ingame.MonsterAttackBroadcast;
import fr.idev.mudserver.network.message.ingame.MonsterAttackResult;
import fr.idev.mudserver.network.message.ingame.NotYourTurn;
import fr.idev.mudserver.network.message.ingame.PlayerAttackBroadcast;
import fr.idev.mudserver.network.message.ingame.YouJoinedCombat;
import fr.idev.mudserver.network.message.ingame.YourTurn;

/**
 * Orchestre le combat au tour par tour au-dessus du calcul pur de
 * {@link CombatService} : décision rejoindre/fusionner/nouvel affrontement,
 * verrouillage, et la cascade qui résout automatiquement les tours de monstres
 * consécutifs (le projet n'a aucune tâche planifiée — voir CLAUDE.md — donc le
 * tour d'un monstre doit être résolu de façon synchrone, dans le même appel que
 * la commande du joueur qui vient de faire avancer le tour).
 *
 * <p>
 * Schéma de verrouillage détaillé dans la Javadoc de {@link CombatEncounter} («
 * verrou B »). Le « verrou A » (moniteur d'un {@link GameMonster}, le même que
 * {@link GameMonster#takeDamage}) n'est jamais tenu ici en même temps que le
 * verrou B — chaque méthode qui a besoin des deux fait le verrou A
 * intégralement, le relâche, puis prend le verrou B.
 *
 * <p>
 * Les messages sont envoyés directement (pas de file d'attente vidée après
 * coup) : {@code Connection#send} (Netty {@code writeAndFlush}) est
 * non-bloquant, donc aucune E/S bloquante n'a lieu sous verrou — et c'est
 * nécessaire pour l'ordre perçu par le joueur, {@link GameMonster#takeDamage}/
 * {@link GamePlayer#takeDamage} publiant {@code CharacterDied}/
 * {@code GamePlayerDied} de façon synchrone, dont les listeners
 * ({@code RoomService}/{@code CharacterService}) envoient déjà leurs propres
 * messages immédiatement : différer les messages de cette classe dans une file
 * vidée après coup les ferait arriver <em>après</em> ceux de la mort/mise à
 * terre qu'ils précèdent pourtant chronologiquement (essai manuel : « The
 * Bandit collapses » apparaissait avant « You attack the Bandit... HIT! »).
 */
@Service
public class CombatEngine {

    private final CombatService combatService;
    private final DiceRoller diceRoller;

    public CombatEngine(CombatService combatService, DiceRoller diceRoller) {
        this.combatService = combatService;
        this.diceRoller = diceRoller;
    }

    public void attack(GamePlayer attacker, GameMonster target) {
        CombatEncounter attackerEncounter = attacker.getEncounter();
        CombatEncounter targetEncounter = target.getEncounter();

        if (attackerEncounter != null && targetEncounter != null) {
            if (attackerEncounter == targetEncounter) {
                performTurnAttack(attackerEncounter, attacker, target);
            } else {
                attacker.send(new AlreadyInAnotherEncounter());
            }
            return;
        }
        if (attackerEncounter != null) {
            // target libre : fusion dans l'affrontement déjà en cours du joueur.
            mergeMonsterInto(attackerEncounter, target);
            return;
        }
        if (targetEncounter != null) {
            // attacker libre : il rejoint l'affrontement déjà en cours du monstre.
            joinPlayerInto(targetEncounter, attacker);
            return;
        }
        startNewEncounter(attacker, target);
    }

    public void useItem(GamePlayer user, Item item) {
        CombatEncounter encounter = user.getEncounter();
        if (encounter == null) {
            user.send(new ItemUseRequiresCombat());
            return;
        }

        synchronized (encounter) {
            if (encounter.currentParticipant() != user) {
                user.send(new NotYourTurn());
            } else {
                user.send(new ItemUseNotImplemented(item.getName()));
                cascade(encounter);
            }
        }
    }

    /**
     * Nouvel affrontement (joueur et monstre tous deux libres) — le verrou A
     * ({@code synchronized(target)}) fait atomiquement le test-et-création, et est
     * intégralement relâché avant toute acquisition du verrou B (
     * {@code synchronized(encounter)}) : jamais imbriqué A-puis-B, même dans la
     * branche de récupération de course ci-dessous (contrairement à une première
     * version de cette méthode, qui appelait {@link #joinPlayerInto} — donc
     * acquérait le verrou B — alors que le verrou A était encore tenu ; un thread
     * concurrent dans {@link #joinPlayerInto} n'a lui-même jamais besoin du verrou
     * A, donc ce sens d'imbrication n'a rien d'inévitable et ouvrait un
     * interblocage possible avec {@link #mergeMonsterInto}, qui prend les deux
     * verrous dans le même ordre). Le coup d'ouverture se résout ensuite <em>sans
     * aucun verrou</em> : ce thread est seul propriétaire de cet affrontement tant
     * qu'il n'a pas été rendu visible autrement, donc rien d'autre ne peut le lire
     * concurremment à ce stade.
     */
    private void startNewEncounter(GamePlayer attacker, GameMonster target) {
        CombatEncounter encounter;
        boolean foundedHere;
        synchronized (target) {
            if (target.getEncounter() != null) {
                encounter = target.getEncounter();
                foundedHere = false;
            } else {
                encounter = new CombatEncounter(target.getCurrentRoom());
                target.setEncounter(encounter);
                attacker.setEncounter(encounter);
                foundedHere = true;
            }
        }

        if (!foundedHere) {
            // Course perdue : un autre thread a déjà créé/rattaché cet affrontement entre
            // la lecture initiale (non verrouillée, dans attack()) et l'acquisition du
            // verrou A ci-dessus.
            joinPlayerInto(encounter, attacker);
            return;
        }

        synchronized (encounter) {
            encounter.joinBeforeInitiative(target);
            encounter.joinBeforeInitiative(attacker);
        }

        CombatResult result = combatService.tryAttack(attacker, target);
        attacker.send(new AttackResult(result));
        target.getCurrentRoom().broadcast(new PlayerAttackBroadcast(attacker.getName(), result), attacker);

        if (result.hit() && target.takeDamage(result.damage(), attacker)) {
            // CombatEngine#onCharacterDied a déjà nettoyé encounter côté monstre ; côté
            // joueur en revanche, rien d'autre ne le fait ici — l'affrontement n'a jamais
            // eu
            // d'ordre d'initiative (le coup d'ouverture est hors ordre), donc le nettoyage
            // de
            // fin d'affrontement porté par resolveFromCurrentTurn n'est jamais atteint dans
            // cette branche.
            attacker.setEncounter(null);
            return;
        }

        synchronized (encounter) {
            // Relit pendingJoiners à l'intérieur du verrou, pour inclure tout rejoignant
            // concurrent arrivé pendant la fenêtre du coup d'ouverture (voir Javadoc de
            // CombatEncounter#establishInitiativeOrder).
            encounter.establishInitiativeOrder(combatService::rollInitiative);
            // Pas d'avanceTurn() ici : le coup d'ouverture est hors ordre d'initiative,
            // donc
            // le participant à l'index 0 n'a encore rien joué dans l'ordre lui-même.
            resolveFromCurrentTurn(encounter);
        }
    }

    private void performTurnAttack(CombatEncounter encounter, GamePlayer attacker, GameMonster target) {
        synchronized (encounter) {
            if (encounter.currentParticipant() != attacker) {
                attacker.send(new NotYourTurn());
                return;
            }
            CombatResult result = combatService.tryAttack(attacker, target);
            attacker.send(new AttackResult(result));
            encounter.getRoom().broadcast(new PlayerAttackBroadcast(attacker.getName(), result), attacker);
            if (result.hit()) {
                target.takeDamage(result.damage(), attacker);
            }
            cascade(encounter);
        }
    }

    private void joinPlayerInto(CombatEncounter encounter, GamePlayer joiner) {
        synchronized (encounter) {
            joiner.setEncounter(encounter);
            insertOrQueue(encounter, joiner);
        }
        joiner.send(new YouJoinedCombat());
        encounter.getRoom().broadcast(new CombatantJoined(joiner.getName()), joiner);
    }

    private void mergeMonsterInto(CombatEncounter encounter, GameMonster joiner) {
        synchronized (joiner) {
            if (joiner.getEncounter() != null) {
                return; // course : déjà rattaché par un autre thread entre-temps.
            }
            joiner.setEncounter(encounter);
        }
        synchronized (encounter) {
            insertOrQueue(encounter, joiner);
        }
        encounter.getRoom().broadcast(new CombatantJoined(joiner.getName()), null);
    }

    /** PRÉCONDITION : appelant détient déjà synchronized(encounter). */
    private void insertOrQueue(CombatEncounter encounter, GameCharacter character) {
        if (encounter.isInitiativeRolled()) {
            int initiative = combatService.rollInitiative(character);
            encounter.insertLatecomer(character, initiative);
        } else {
            encounter.joinBeforeInitiative(character);
        }
    }

    /**
     * PRÉCONDITION : appelant détient déjà synchronized(encounter) ; fait avancer
     * le tour.
     */
    private void cascade(CombatEncounter encounter) {
        encounter.advanceTurn();
        resolveFromCurrentTurn(encounter);
    }

    /**
     * PRÉCONDITION : appelant détient déjà synchronized(encounter) ; résout, à
     * partir de la position <em>courante</em> du pointeur (sans avancer d'abord),
     * tous les tours de monstres consécutifs jusqu'à retomber sur un joueur ou la
     * fin de l'affrontement.
     */
    private void resolveFromCurrentTurn(CombatEncounter encounter) {
        while (!encounter.isOver() && encounter.currentParticipant() instanceof GameMonster monster) {
            List<GamePlayer> livingPlayers = encounter.livingPlayers();
            if (livingPlayers.isEmpty()) {
                break;
            }
            GamePlayer victim = livingPlayers.size() == 1
                    ? livingPlayers.get(0)
                    : livingPlayers.get(diceRoller.roll(new DiceExpression(1, livingPlayers.size(), 0)).total() - 1);

            CombatResult result = combatService.tryMonsterAttack(monster, victim);
            victim.send(new MonsterAttackResult(monster.getName(), result));
            encounter.getRoom().broadcast(new MonsterAttackBroadcast(monster.getName(), result), victim);
            if (result.hit()) {
                victim.takeDamage(result.damage(), monster);
            }
            encounter.advanceTurn();
        }

        if (encounter.isOver()) {
            boolean playersWon = encounter.participants().stream().noneMatch(GameMonster.class::isInstance);
            for (GameCharacter participant : encounter.participants()) {
                participant.setEncounter(null);
            }
            encounter.getRoom().broadcast(new EncounterEnded(playersWon), null);
        } else if (encounter.currentParticipant() instanceof GamePlayer nextPlayer) {
            nextPlayer.send(new YourTurn());
        }
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        GameMonster monster = event.character();
        CombatEncounter encounter = monster.getEncounter();
        if (encounter == null) {
            return;
        }
        encounter.remove(monster);
        monster.setEncounter(null);
    }

    @EventListener
    void onGamePlayerDied(GamePlayerDied event) {
        GamePlayer player = event.character();
        CombatEncounter encounter = player.getEncounter();
        if (encounter == null) {
            return;
        }
        encounter.remove(player);
        player.setEncounter(null);
    }
}

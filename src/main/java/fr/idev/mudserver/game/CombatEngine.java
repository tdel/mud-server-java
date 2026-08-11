package fr.idev.mudserver.game;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.ConsumableItem;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.ActionEconomy;
import fr.idev.mudserver.domain.actor.CombatEncounter;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.message.ingame.ActionsRemaining;
import fr.idev.mudserver.network.message.ingame.AlreadyInAnotherEncounter;
import fr.idev.mudserver.network.message.ingame.AttackResult;
import fr.idev.mudserver.network.message.ingame.CombatantJoined;
import fr.idev.mudserver.network.message.ingame.EncounterEnded;
import fr.idev.mudserver.network.message.ingame.ItemNotUsable;
import fr.idev.mudserver.network.message.ingame.MonsterAggroBroadcast;
import fr.idev.mudserver.network.message.ingame.MonsterAggroTriggered;
import fr.idev.mudserver.network.message.ingame.MonsterAttackBroadcast;
import fr.idev.mudserver.network.message.ingame.MonsterAttackResult;
import fr.idev.mudserver.network.message.ingame.NotYourTurn;
import fr.idev.mudserver.network.message.ingame.PlayerAttackBroadcast;
import fr.idev.mudserver.network.message.ingame.YouJoinedCombat;
import fr.idev.mudserver.network.message.ingame.YourTurn;

@Service
public class CombatEngine {

    private static final Logger log = LoggerFactory.getLogger(CombatEngine.class);

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
        if (!(item.getTemplate() instanceof ConsumableItem consumable)) {
            user.send(new ItemNotUsable(item.getName()));
            return;
        }

        CombatEncounter encounter = user.getEncounter();
        if (encounter == null) {
            consumable.consume(user, item);
            return;
        }

        synchronized (encounter) {
            if (encounter.currentParticipant() != user || !user.getActionEconomy().trySpendAction()) {
                user.send(new NotYourTurn());
            } else {
                consumable.consume(user, item);
                continueOrEndTurn(encounter, user);
            }
        }
    }

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

        CombatResult result = attacker.tryAttack(target);
        attacker.send(new AttackResult(result));
        target.getCurrentRoom().broadcast(new PlayerAttackBroadcast(attacker.getName(), result), attacker);
        log.info("combat.attack_resolved attacker={} target={} hit={} critical={} damage={}", attacker.getName(),
                target.getName(), result.hit(), result.criticalHit(), result.damage());

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
            encounter.establishInitiativeOrder(GameCharacter::rollInitiative);
            // Pas d'avanceTurn() ici : le coup d'ouverture est hors ordre d'initiative,
            // donc
            // le participant à l'index 0 n'a encore rien joué dans l'ordre lui-même.
            resolveFromCurrentTurn(encounter);
        }
    }

    private CombatEncounter startAggroEncounter(GameMonster founder, GamePlayer victim) {
        CombatEncounter encounter;
        boolean foundedHere;
        synchronized (founder) {
            if (founder.getEncounter() != null) {
                encounter = founder.getEncounter();
                foundedHere = false;
            } else {
                encounter = new CombatEncounter(founder.getCurrentRoom());
                founder.setEncounter(encounter);
                foundedHere = true;
            }
        }

        if (!foundedHere) {
            // Course perdue : une autre victime a déjà fondé cet affrontement avec le même
            // monstre entre la lecture initiale (non verrouillée, dans
            // onGamePlayerEnteredCell) et l'acquisition du verrou A ci-dessus.
            joinPlayerInto(encounter, victim);
            return encounter;
        }

        victim.setEncounter(encounter);
        synchronized (encounter) {
            encounter.joinBeforeInitiative(founder);
            encounter.joinBeforeInitiative(victim);
            victim.send(new MonsterAggroTriggered(founder.getName()));
            encounter.getRoom().broadcast(new MonsterAggroBroadcast(victim.getName(), founder.getName()), victim);
            encounter.establishInitiativeOrder(GameCharacter::rollInitiative);
            resolveFromCurrentTurn(encounter);
        }
        return encounter;
    }

    private void performTurnAttack(CombatEncounter encounter, GamePlayer attacker, GameMonster target) {
        synchronized (encounter) {
            if (encounter.currentParticipant() != attacker || !attacker.getActionEconomy().trySpendAction()) {
                attacker.send(new NotYourTurn());
                return;
            }
            CombatResult result = attacker.tryAttack(target);
            attacker.send(new AttackResult(result));
            encounter.getRoom().broadcast(new PlayerAttackBroadcast(attacker.getName(), result), attacker);
            log.info("combat.attack_resolved attacker={} target={} hit={} critical={} damage={}", attacker.getName(),
                    target.getName(), result.hit(), result.criticalHit(), result.damage());
            if (result.hit()) {
                target.takeDamage(result.damage(), attacker);
            }
            continueOrEndTurn(encounter, attacker);
        }
    }

    private void continueOrEndTurn(CombatEncounter encounter, GamePlayer actor) {
        ActionEconomy economy = actor.getActionEconomy();
        if (economy.hasActionRemaining()) {
            actor.send(new ActionsRemaining(economy.getActionsRemaining(), economy.getExtraActionsRemaining()));
        } else {
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

    private void insertOrQueue(CombatEncounter encounter, GameCharacter character) {
        if (encounter.isInitiativeRolled()) {
            int initiative = character.rollInitiative();
            encounter.insertLatecomer(character, initiative);
        } else {
            encounter.joinBeforeInitiative(character);
        }
    }

    private void cascade(CombatEncounter encounter) {
        encounter.advanceTurn();
        resolveFromCurrentTurn(encounter);
    }

    private void resolveFromCurrentTurn(CombatEncounter encounter) {
        while (!encounter.isOver() && encounter.currentParticipant() instanceof GameMonster monster) {
            monster.getActionEconomy().resetForTurn();
            List<GamePlayer> livingPlayers = encounter.livingPlayers();
            if (livingPlayers.isEmpty()) {
                break;
            }
            GamePlayer victim = livingPlayers.size() == 1
                    ? livingPlayers.get(0)
                    : livingPlayers.get(DiceRoller.roll(new DiceExpression(1, livingPlayers.size(), 0)).total() - 1);

            CombatResult result = monster.tryAttack(victim);
            victim.send(new MonsterAttackResult(monster.getName(), result));
            encounter.getRoom().broadcast(new MonsterAttackBroadcast(monster.getName(), result), victim);
            log.info("combat.monster_attack_resolved monster={} victim={} hit={} damage={}", monster.getName(),
                    victim.getName(), result.hit(), result.damage());
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
            log.info("combat.encounter_ended room={} playersWon={}", encounter.getRoom().getName(), playersWon);
        } else if (encounter.currentParticipant() instanceof GamePlayer nextPlayer) {
            nextPlayer.getActionEconomy().resetForTurn();
            ActionEconomy economy = nextPlayer.getActionEconomy();
            nextPlayer.send(new YourTurn(economy.getActionsRemaining(), economy.getExtraActionsRemaining()));
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
        log.debug("combat.encounter_monster_removed monster={}", monster.getName());
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
        log.debug("combat.encounter_player_removed player={}", player.getName());
    }

    @EventListener
    void onGamePlayerEnteredCell(GamePlayerEnteredCell event) {
        GamePlayer victim = event.character();
        if (victim.isInCombat()) {
            return;
        }

        List<GameMonster> aggressors = victim.getCurrentRoom().getMonsters().stream()
                .filter(monster -> monster.getCurrentHealth() > 0)
                .filter(monster -> monster.getPosition().distanceTo(event.cell()) <= monster.getPresenceRadius())
                .toList();
        if (aggressors.isEmpty()) {
            return;
        }

        log.info("combat.aggro_triggered victim={} aggressors={}", victim.getName(),
                aggressors.stream().map(GameMonster::getName).toList());

        Optional<GameMonster> alreadyFighting = aggressors.stream().filter(monster -> monster.getEncounter() != null)
                .findFirst();
        if (alreadyFighting.isPresent()) {
            joinPlayerInto(alreadyFighting.get().getEncounter(), victim);
        } else {
            startAggroEncounter(aggressors.get(0), victim);
        }

        if (!victim.isInCombat()) {
            // Le combat vient déjà de se terminer (ex. le monstre gagne l'initiative et
            // tue la victime dès le premier tour) avant d'avoir pu agréger le reste des
            // monstres à portée.
            return;
        }
        CombatEncounter encounter = victim.getEncounter();
        for (GameMonster monster : aggressors) {
            if (monster.getEncounter() == null) {
                mergeMonsterInto(encounter, monster);
            }
        }
    }
}

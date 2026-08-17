package fr.idev.mudserver.game;

import fr.idev.mudserver.domain.actor.component.IdentityComponent;

import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.actor.component.AggroComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.component.CombatComponent;
import fr.idev.mudserver.domain.actor.system.AiSystem;
import fr.idev.mudserver.domain.item.ConsumableItem;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.combat.CombatEncounter;
import fr.idev.mudserver.domain.actor.system.CombatSystem;
import fr.idev.mudserver.domain.actor.system.DiceSystem;
import fr.idev.mudserver.domain.actor.system.EncounterSystem;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.actor.system.NetworkSystem;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.event.CharacterDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
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

    private final CombatSystem combatSystem;
    private final DiceSystem diceSystem;
    private final AiSystem aiSystem;
    private final InventorySystem inventorySystem;
    private final EncounterSystem encounterSystem;
    private final NetworkSystem networkSystem;

    public CombatEngine(CombatSystem combatSystem, DiceSystem diceSystem, AiSystem aiSystem,
            InventorySystem inventorySystem, EncounterSystem encounterSystem, NetworkSystem networkSystem) {
        this.combatSystem = combatSystem;
        this.diceSystem = diceSystem;
        this.aiSystem = aiSystem;
        this.inventorySystem = inventorySystem;
        this.encounterSystem = encounterSystem;
        this.networkSystem = networkSystem;
    }

    public void attack(CharacterInstance attacker, MonsterInstance target) {
        CombatEncounter attackerEncounter = encounterSystem.getEncounter(attacker);
        CombatEncounter targetEncounter = encounterSystem.getEncounter(target);

        if (attackerEncounter != null && targetEncounter != null) {
            if (attackerEncounter == targetEncounter) {
                performTurnAttack(attackerEncounter, attacker, target);
            } else {
                networkSystem.send(attacker, new AlreadyInAnotherEncounter());
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

    public void useItem(CharacterInstance user, Item item) {
        if (!(item.getTemplate() instanceof ConsumableItem consumable)) {
            networkSystem.send(user, new ItemNotUsable(item.getName()));
            return;
        }

        CombatEncounter encounter = encounterSystem.getEncounter(user);
        if (encounter == null) {
            consumable.consume(user, item, combatSystem, inventorySystem);
            return;
        }

        synchronized (encounter) {
            if (encounter.currentParticipant() != user || !combatSystem.trySpendAction(user)) {
                networkSystem.send(user, new NotYourTurn());
            } else {
                consumable.consume(user, item, combatSystem, inventorySystem);
                continueOrEndTurn(encounter, user);
            }
        }
    }

    private void startNewEncounter(CharacterInstance attacker, MonsterInstance target) {
        CombatEncounter encounter;
        boolean foundedHere;
        synchronized (target) {
            CombatEncounter existing = encounterSystem.getEncounter(target);
            if (existing != null) {
                encounter = existing;
                foundedHere = false;
            } else {
                encounter = encounterSystem.createEncounter(target.component(PositionComponent.class).currentRoom());
                encounterSystem.join(target, encounter);
                encounterSystem.join(attacker, encounter);
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

        CombatResult result = combatSystem.tryAttack(attacker, target);
        networkSystem.send(attacker, new AttackResult(result));
        target.component(PositionComponent.class).currentRoom().broadcast(
                new PlayerAttackBroadcast(attacker.component(IdentityComponent.class).name(), result), attacker);
        log.info("combat.attack_resolved attacker={} target={} hit={} critical={} damage={}",
                attacker.component(IdentityComponent.class).name(), target.component(IdentityComponent.class).name(),
                result.hit(), result.criticalHit(), result.damage());

        if (result.hit() && combatSystem.applyDamage(target, result.damage(), attacker)) {
            // CombatEngine#onCharacterDied a déjà nettoyé encounter côté monstre ; côté
            // joueur en revanche, rien d'autre ne le fait ici — l'affrontement n'a jamais
            // eu
            // d'ordre d'initiative (le coup d'ouverture est hors ordre), donc le nettoyage
            // de
            // fin d'affrontement porté par resolveFromCurrentTurn n'est jamais atteint dans
            // cette branche.
            encounterSystem.leave(attacker);
            encounterSystem.endEncounter(encounter);
            return;
        }

        synchronized (encounter) {
            // Relit pendingJoiners à l'intérieur du verrou, pour inclure tout rejoignant
            // concurrent arrivé pendant la fenêtre du coup d'ouverture (voir Javadoc de
            // CombatEncounter#establishInitiativeOrder).
            encounter.establishInitiativeOrder(diceSystem::rollInitiative);
            // Pas d'avanceTurn() ici : le coup d'ouverture est hors ordre d'initiative,
            // donc
            // le participant à l'index 0 n'a encore rien joué dans l'ordre lui-même.
            resolveFromCurrentTurn(encounter);
        }
    }

    private CombatEncounter startAggroEncounter(MonsterInstance founder, CharacterInstance victim) {
        CombatEncounter encounter;
        boolean foundedHere;
        synchronized (founder) {
            CombatEncounter existing = encounterSystem.getEncounter(founder);
            if (existing != null) {
                encounter = existing;
                foundedHere = false;
            } else {
                encounter = encounterSystem.createEncounter(founder.component(PositionComponent.class).currentRoom());
                encounterSystem.join(founder, encounter);
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

        encounterSystem.join(victim, encounter);
        synchronized (encounter) {
            encounter.joinBeforeInitiative(founder);
            encounter.joinBeforeInitiative(victim);
            networkSystem.send(victim, new MonsterAggroTriggered(founder.component(IdentityComponent.class).name()));
            encounter.getRoom().broadcast(new MonsterAggroBroadcast(victim.component(IdentityComponent.class).name(),
                    founder.component(IdentityComponent.class).name()), victim);
            encounter.establishInitiativeOrder(diceSystem::rollInitiative);
            resolveFromCurrentTurn(encounter);
        }
        return encounter;
    }

    private void performTurnAttack(CombatEncounter encounter, CharacterInstance attacker, MonsterInstance target) {
        synchronized (encounter) {
            if (encounter.currentParticipant() != attacker || !combatSystem.trySpendAction(attacker)) {
                networkSystem.send(attacker, new NotYourTurn());
                return;
            }
            CombatResult result = combatSystem.tryAttack(attacker, target);
            networkSystem.send(attacker, new AttackResult(result));
            encounter.getRoom().broadcast(
                    new PlayerAttackBroadcast(attacker.component(IdentityComponent.class).name(), result), attacker);
            log.info("combat.attack_resolved attacker={} target={} hit={} critical={} damage={}",
                    attacker.component(IdentityComponent.class).name(),
                    target.component(IdentityComponent.class).name(), result.hit(), result.criticalHit(),
                    result.damage());
            if (result.hit()) {
                combatSystem.applyDamage(target, result.damage(), attacker);
            }
            continueOrEndTurn(encounter, attacker);
        }
    }

    private void continueOrEndTurn(CombatEncounter encounter, CharacterInstance actor) {
        if (combatSystem.hasActionRemaining(actor)) {
            CombatComponent combat = actor.component(CombatComponent.class);
            networkSystem.send(actor, new ActionsRemaining(combat.actionsRemaining(), combat.extraActionsRemaining()));
        } else {
            cascade(encounter);
        }
    }

    private void joinPlayerInto(CombatEncounter encounter, CharacterInstance joiner) {
        synchronized (encounter) {
            encounterSystem.join(joiner, encounter);
            insertOrQueue(encounter, joiner);
        }
        networkSystem.send(joiner, new YouJoinedCombat());
        encounter.getRoom().broadcast(new CombatantJoined(joiner.component(IdentityComponent.class).name()), joiner);
    }

    private void mergeMonsterInto(CombatEncounter encounter, MonsterInstance joiner) {
        synchronized (joiner) {
            if (encounterSystem.getEncounter(joiner) != null) {
                return; // course : déjà rattaché par un autre thread entre-temps.
            }
            encounterSystem.join(joiner, encounter);
        }
        synchronized (encounter) {
            insertOrQueue(encounter, joiner);
        }
        encounter.getRoom().broadcast(new CombatantJoined(joiner.component(IdentityComponent.class).name()), null);
    }

    private void insertOrQueue(CombatEncounter encounter, AbstractCharacter character) {
        if (encounter.isInitiativeRolled()) {
            int initiative = diceSystem.rollInitiative(character);
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
        while (!encounter.isOver() && encounter.currentParticipant() instanceof MonsterInstance monster) {
            combatSystem.resetForTurn(monster);
            List<CharacterInstance> livingPlayers = encounter.livingPlayers();
            if (livingPlayers.isEmpty()) {
                break;
            }
            CharacterInstance victim = aiSystem.chooseTarget(monster, livingPlayers);

            CombatResult result = combatSystem.tryAttack(monster, victim);
            networkSystem.send(victim,
                    new MonsterAttackResult(monster.component(IdentityComponent.class).name(), result));
            encounter.getRoom().broadcast(
                    new MonsterAttackBroadcast(monster.component(IdentityComponent.class).name(), result), victim);
            log.info("combat.monster_attack_resolved monster={} victim={} hit={} damage={}",
                    monster.component(IdentityComponent.class).name(), victim.component(IdentityComponent.class).name(),
                    result.hit(), result.damage());
            if (result.hit()) {
                combatSystem.applyDamage(victim, result.damage(), monster);
            }
            encounter.advanceTurn();
        }

        if (encounter.isOver()) {
            boolean playersWon = encounter.participants().stream().noneMatch(MonsterInstance.class::isInstance);
            for (AbstractCharacter participant : encounter.participants()) {
                encounterSystem.leave(participant);
            }
            encounterSystem.endEncounter(encounter);
            encounter.getRoom().broadcast(new EncounterEnded(playersWon), null);
            log.info("combat.encounter_ended room={} playersWon={}", encounter.getRoom().getName(), playersWon);
        } else if (encounter.currentParticipant() instanceof CharacterInstance nextPlayer) {
            combatSystem.resetForTurn(nextPlayer);
            CombatComponent combat = nextPlayer.component(CombatComponent.class);
            networkSystem.send(nextPlayer, new YourTurn(combat.actionsRemaining(), combat.extraActionsRemaining()));
        }
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        MonsterInstance monster = event.character();
        CombatEncounter encounter = encounterSystem.getEncounter(monster);
        if (encounter == null) {
            return;
        }
        encounter.remove(monster);
        encounterSystem.leave(monster);
        aiSystem.clearTarget(monster);
        log.debug("combat.encounter_monster_removed monster={}", monster.component(IdentityComponent.class).name());
    }

    @EventListener
    void onGamePlayerDied(GamePlayerDied event) {
        CharacterInstance player = event.character();
        CombatEncounter encounter = encounterSystem.getEncounter(player);
        if (encounter == null) {
            return;
        }
        encounter.remove(player);
        encounterSystem.leave(player);
        log.debug("combat.encounter_player_removed player={}", player.component(IdentityComponent.class).name());
    }

    @EventListener
    void onGamePlayerEnteredCell(GamePlayerEnteredCell event) {
        CharacterInstance victim = event.character();
        if (encounterSystem.isInCombat(victim)) {
            return;
        }

        List<MonsterInstance> aggressors = victim.component(PositionComponent.class).currentRoom().getMonsters()
                .stream().filter(monster -> monster.component(CombatComponent.class).currentHealth() > 0)
                .filter(monster -> monster.component(PositionComponent.class).hexCoordinate()
                        .distanceTo(event.cell()) <= monster.component(AggroComponent.class).aggroRadius())
                .toList();
        if (aggressors.isEmpty()) {
            return;
        }

        log.info("combat.aggro_triggered victim={} aggressors={}", victim.component(IdentityComponent.class).name(),
                aggressors.stream().map(monster -> monster.component(IdentityComponent.class).name()).toList());

        Optional<MonsterInstance> alreadyFighting = aggressors.stream()
                .filter(monster -> encounterSystem.getEncounter(monster) != null).findFirst();
        if (alreadyFighting.isPresent()) {
            joinPlayerInto(encounterSystem.getEncounter(alreadyFighting.get()), victim);
        } else {
            startAggroEncounter(aggressors.get(0), victim);
        }

        if (!encounterSystem.isInCombat(victim)) {
            // Le combat vient déjà de se terminer (ex. le monstre gagne l'initiative et
            // tue la victime dès le premier tour) avant d'avoir pu agréger le reste des
            // monstres à portée.
            return;
        }
        CombatEncounter encounter = encounterSystem.getEncounter(victim);
        for (MonsterInstance monster : aggressors) {
            if (encounterSystem.getEncounter(monster) == null) {
                mergeMonsterInto(encounter, monster);
            }
        }
    }
}

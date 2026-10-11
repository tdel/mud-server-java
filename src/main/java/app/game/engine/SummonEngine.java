package app.game.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.SkillElement;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.GamePlayerMovedToMap;
import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.actor.template.SummonTemplate;
import app.domain.actor.template.SummonTemplate.SummonSpecial;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.game.catalog.SummonCatalog;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.CharacterMovementStarted;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.RegenTick;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SummonDismissed;
import app.network.message.ingame.SummonStatus;

// Serviteurs invoqués : apparition près du maître, IA (le suit, attaque ce
// qu'il attaque ou ce qui l'attaque, se défend), capacités propres (Kat
// recharge les PM de son maître, Mew foudroie sa cible, le Corrupted Man vole
// de la vie), fin de vie (durée écoulée, mort, déconnexion, "unsummon").
// L'état du serviteur est poussé à son maître par SummonStatus.
@Component
public class SummonEngine {

    private static final Logger log = LoggerFactory.getLogger(SummonEngine.class);

    private static final long TICK_INTERVAL_MS = 250L;
    // Distance à laquelle le serviteur se tient de son maître, et au-delà de
    // laquelle il le rejoint.
    private static final double FOLLOW_DISTANCE = 1.6;
    private static final double FOLLOW_SLACK = 3.0;
    // Trop loin (téléportation du maître, etc.) : le serviteur réapparaît à côté.
    private static final double TELEPORT_DISTANCE = 25.0;
    // Capacités propres : période et puissance.
    private static final long SPECIAL_PERIOD_MS = 15_000L;
    private static final int RECHARGE_OWNER_AMOUNT = 40;
    private static final int STORM_POWER = 30;
    private static final int DRAIN_PERCENT = 20;
    private static final UUID STORM_SKILL_ID = UUID.nameUUIDFromBytes("summon:Mega Storm Strike".getBytes());
    private static final UUID RECHARGE_SKILL_ID = UUID.nameUUIDFromBytes("summon:Master Recharge".getBytes());

    private final Map<UUID, SummonInstance> summons = new ConcurrentHashMap<>();
    private final SummonCatalog summonCatalog;
    private final MovementEngine movementEngine;
    private final SkillCastEngine skillCastEngine;

    public SummonEngine(SummonCatalog summonCatalog, MovementEngine movementEngine, SkillCastEngine skillCastEngine) {
        this.summonCatalog = summonCatalog;
        this.movementEngine = movementEngine;
        this.skillCastEngine = skillCastEngine;
    }

    public boolean summon(PlayerInstance owner, ActiveSkill activeSkill, int level) {
        if (owner.getSummon() != null || activeSkill.summonId() == null) {
            return false;
        }
        SummonTemplate template = summonCatalog.findById(activeSkill.summonId()).orElse(null);
        if (template == null) {
            log.warn("summon.unknown_template skill={} summonId={}", activeSkill.name(), activeSkill.summonId());
            return false;
        }
        SummonInstance summon = new SummonInstance(owner, template, level);
        MapInstance map = owner.getMotionSystem().getCurrentMap();
        Position spawn = besideOwner(owner, map);
        summon.getMotionSystem().setHeading(owner.getMotionSystem().getHeading());
        map.addSummon(summon, spawn);
        owner.setSummon(summon);
        summons.put(summon.getId(), summon);
        summon.setNextSpecialAtMillis(System.currentTimeMillis() + SPECIAL_PERIOD_MS);
        summon.getKnownList().populate();
        sendStatus(summon);
        log.info("summon.appeared owner={} summon={} level={}", owner.getName(), template.name(), level);
        return true;
    }

    private static Position besideOwner(PlayerInstance owner, MapInstance map) {
        Position position = owner.getMotionSystem().getPosition();
        double heading = owner.getMotionSystem().getHeading() + Math.PI * 0.75;
        Position candidate = new Position(position.x() + Math.cos(heading) * FOLLOW_DISTANCE,
                position.y() + Math.sin(heading) * FOLLOW_DISTANCE);
        return map.isWalkable(candidate) ? candidate : position;
    }

    // Renvoie le serviteur (commande unsummon, fin de vie, mort, départ du
    // maître) ; `reason` est affiché au maître.
    public void dismiss(SummonInstance summon, String reason) {
        if (summons.remove(summon.getId()) == null) {
            return;
        }
        PlayerInstance owner = summon.getOwner();
        if (owner.getSummon() == summon) {
            owner.setSummon(null);
        }
        movementEngine.stopMovement(summon);
        skillCastEngine.cancelCast(summon);
        MapInstance map = summon.getMotionSystem().getCurrentMap();
        if (map != null) {
            summon.getKnownList().clear();
            map.removeSummon(summon);
        }
        owner.send(new SummonDismissed(summon.getId(), summon.getName(), reason));
        log.info("summon.dismissed owner={} summon={} reason={}", owner.getName(), summon.getName(), reason);
    }

    public void sendStatus(SummonInstance summon) {
        long remaining = Math.max(0, summon.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        summon.getOwner()
                .send(new SummonStatus(summon.getId(), summon.getName(), summon.getTemplate().model(),
                        summon.getLevelingSystem().getLevel(), summon.getResourceSystem().getCurrentHealth(),
                        summon.getResourceSystem().getMaxHealth(), remaining));
    }

    // Le serviteur prend pour cible ce que son maître attaque, et ce qui
    // attaque son maître ou lui-même.
    @EventListener
    void onCharacterBeginAttack(CharacterBeginAttack event) {
        if (event.attacker() instanceof PlayerInstance owner && owner.getSummon() != null
                && event.defender() instanceof MonsterInstance) {
            engage(owner.getSummon(), event.defender());
        } else if (event.attacker() instanceof MonsterInstance monster) {
            if (event.defender() instanceof PlayerInstance owner && owner.getSummon() != null) {
                engageIfIdle(owner.getSummon(), monster);
            } else if (event.defender() instanceof SummonInstance summon) {
                engageIfIdle(summon, monster);
            }
        }
    }

    private static void engage(SummonInstance summon, AbstractCharacter target) {
        summon.setCombatTarget(target);
    }

    private static void engageIfIdle(SummonInstance summon, AbstractCharacter target) {
        AbstractCharacter current = summon.getCombatTarget();
        if (current == null || current.getResourceSystem().getCurrentHealth() <= 0) {
            summon.setCombatTarget(target);
        }
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        if (event.character() instanceof SummonInstance summon && summons.containsKey(summon.getId())) {
            sendStatus(summon);
        }
    }

    @EventListener
    void onCharacterDied(CharacterDied event) {
        if (event.character() instanceof SummonInstance summon) {
            dismiss(summon, "Votre serviteur est mort.");
        } else if (event.character() instanceof PlayerInstance owner && owner.getSummon() != null) {
            dismiss(owner.getSummon(), "Votre serviteur disparaît avec vous.");
        } else if (event.character() instanceof MonsterInstance) {
            for (SummonInstance summon : summons.values()) {
                if (summon.getCombatTarget() == event.character()) {
                    summon.setCombatTarget(null);
                }
            }
        }
    }

    // Le serviteur suit son maître d'une carte à l'autre.
    @EventListener
    void onGamePlayerMovedToMap(GamePlayerMovedToMap event) {
        SummonInstance summon = event.character().getSummon();
        if (summon == null) {
            return;
        }
        movementEngine.stopMovement(summon);
        summon.getKnownList().clear();
        event.from().removeSummon(summon);
        summon.setCombatTarget(null);
        event.to().addSummon(summon, besideOwner(event.character(), event.to()));
        summon.getKnownList().populate();
    }

    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        if (event.character().getSummon() != null) {
            dismiss(event.character().getSummon(), "Votre serviteur repart.");
        }
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long nowNanos = System.nanoTime();
        for (SummonInstance summon : summons.values()) {
            try {
                tickSummon(summon, nowNanos);
            } catch (Exception e) {
                log.error("summon.tick_failed summon={}", summon.getId(), e);
            }
        }
    }

    private void tickSummon(SummonInstance summon, long nowNanos) {
        PlayerInstance owner = summon.getOwner();
        if (!Instant.now().isBefore(summon.getExpiresAt())) {
            dismiss(summon, "Le temps d'invocation de votre serviteur est écoulé.");
            return;
        }
        MapInstance map = summon.getMotionSystem().getCurrentMap();
        if (owner.getMotionSystem().getCurrentMap() != map || !map.isPresent(owner)) {
            return;
        }
        if (summon.getEffectsSystem().isIncapacitated()) {
            stop(summon);
            summon.setLastStepAtNanos(nowNanos);
            return;
        }
        Position ownerPosition = owner.getMotionSystem().getPosition();
        if (summon.getMotionSystem().getPosition().distanceTo(ownerPosition) > TELEPORT_DISTANCE) {
            stop(summon);
            summon.getMotionSystem().setPosition(besideOwner(owner, map));
            summon.getKnownList().refresh();
            summon.setCombatTarget(null);
        }
        useSpecial(summon);

        AbstractCharacter target = summon.getCombatTarget();
        if (target != null && (target.getResourceSystem().getCurrentHealth() <= 0 || !map.isPresent(target))) {
            summon.setCombatTarget(null);
            target = null;
        }
        if (target != null) {
            double distance = summon.getMotionSystem().getPosition().distanceTo(target.getMotionSystem().getPosition());
            if (distance <= CombatFormulas.ATTACK_RANGE) {
                stop(summon);
                summon.setLastStepAtNanos(nowNanos);
                attack(summon, target);
            } else {
                step(summon, target.getMotionSystem().getPosition(), nowNanos);
            }
            return;
        }
        if (summon.getMotionSystem().getPosition().distanceTo(ownerPosition) > FOLLOW_SLACK) {
            Position toward = ownerPosition.moveToward(summon.getMotionSystem().getPosition(), FOLLOW_DISTANCE);
            step(summon, toward, nowNanos);
        } else {
            stop(summon);
            summon.setLastStepAtNanos(nowNanos);
        }
    }

    private static void attack(SummonInstance summon, AbstractCharacter target) {
        int healthBefore = target.getResourceSystem().getCurrentHealth();
        summon.getCombatSystem().attack(target);
        if (summon.getTemplate().special() == SummonSpecial.DRAIN) {
            int dealt = healthBefore - target.getResourceSystem().getCurrentHealth();
            if (dealt > 0) {
                summon.getResourceSystem().heal(dealt * DRAIN_PERCENT / 100);
            }
        }
    }

    // Kat : Master Recharge (PM du maître) ; Mew : Mega Storm Strike sur sa cible.
    private void useSpecial(SummonInstance summon) {
        SummonSpecial special = summon.getTemplate().special();
        long now = System.currentTimeMillis();
        if (special == null || special == SummonSpecial.DRAIN || now < summon.getNextSpecialAtMillis()) {
            return;
        }
        PlayerInstance owner = summon.getOwner();
        if (special == SummonSpecial.RECHARGE_OWNER) {
            if (owner.getResourceSystem().getCurrentMana() >= owner.getResourceSystem().getMaxMana()) {
                return;
            }
            int gained = owner.getResourceSystem()
                    .gainMana(RECHARGE_OWNER_AMOUNT + owner.getStatSystem().adjust(ModifiedStat.MANA_CHARGE, 0));
            summon.setNextSpecialAtMillis(now + SPECIAL_PERIOD_MS);
            summon.broadcast(new SkillCastAnnounced(summon.getId(), summon.getName(), RECHARGE_SKILL_ID,
                    "Master Recharge", owner.getId(), owner.getName(), false, true, gained,
                    owner.getResourceSystem().getCurrentHealth(), owner.getResourceSystem().getMaxHealth(), false),
                    null);
            owner.send(new RegenTick(0, gained, owner.getResourceSystem().getCurrentHealth(),
                    owner.getResourceSystem().getMaxHealth(), owner.getResourceSystem().getCurrentMana(),
                    owner.getResourceSystem().getMaxMana()));
            return;
        }
        AbstractCharacter target = summon.getCombatTarget();
        if (target == null
                || summon.getMotionSystem().getPosition().distanceTo(target.getMotionSystem().getPosition()) > 30) {
            return;
        }
        int damage = CombatFormulas.resolveMagicalDamage(summon.getStatSystem().getEffective(ModifiedStat.MATK),
                target.getStatSystem().getEffective(ModifiedStat.MDEF), STORM_POWER, false, false);
        damage = CombatFormulas.applyElementalResistance(damage, target.elementalResistance(SkillElement.WIND));
        summon.setNextSpecialAtMillis(now + SPECIAL_PERIOD_MS);
        boolean defeated = target.takeDamage(damage, summon);
        summon.broadcast(new SkillCastAnnounced(summon.getId(), summon.getName(), STORM_SKILL_ID, "Mega Storm Strike",
                target.getId(), target.getName(), false, true, damage, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), defeated), null);
    }

    private static void step(SummonInstance summon, Position destination, long nowNanos) {
        if (!summon.getEffectsSystem().canMove()) {
            return;
        }
        MapInstance map = summon.getMotionSystem().getCurrentMap();
        Position previous = summon.getMotionSystem().getPosition();
        double dtSeconds = Math.min(0.5, (nowNanos - summon.getLastStepAtNanos()) / 1_000_000_000.0);
        ContinuousStep.StepResult result = ContinuousStep.step(previous, List.of(destination),
                MovementEngine.unitsPerSecond(summon.getMotionSystem().getSpeed()), dtSeconds, map.getCollisionGrid());
        summon.getMotionSystem().setPosition(result.position());
        if (!result.position().equals(previous)) {
            summon.getMotionSystem().setHeading(previous.headingTo(result.position()));
        }
        summon.setLastStepAtNanos(nowNanos);
        summon.getKnownList().refresh();
        summon.setMoving(true);
        summon.broadcast(new CharacterMovementStarted(summon.getId(), summon.getName(), result.position().x(),
                result.position().y(), destination.x(), destination.y(), summon.getMotionSystem().getHeading(),
                List.of(destination)), null);
    }

    private static void stop(SummonInstance summon) {
        if (!summon.isMoving()) {
            return;
        }
        summon.setMoving(false);
        summon.broadcast(new CharacterMovementStopped(summon.getId(), summon.getName(),
                summon.getMotionSystem().getPosition().x(), summon.getMotionSystem().getPosition().y()), null);
    }
}

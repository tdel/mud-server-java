package app.game.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.domain.actor.event.PlayerRemovedFromWorld;
import app.domain.ActiveSkill;
import app.domain.SkillDamageType;
import app.domain.SkillEffectType;
import app.domain.SkillTargetType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.ModifiedStat;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.ShotGradeDepleted;
import app.domain.actor.event.SkillCast;
import app.domain.actor.event.SkillCastBegin;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.domain.map.Position;
import app.domain.world.Corpse;
import app.domain.world.MapInstance;
import app.game.Randomizer;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.CharacterBlinked;
import app.network.message.ingame.SkillCastAnnounced;
import app.network.message.ingame.SkillCastCancelled;
import app.network.message.ingame.SkillCastStarted;
import app.network.message.ingame.SkillDrained;
import app.network.message.ingame.SkillFizzled;
import app.network.message.ingame.SkillModifierAnnounced;
import app.network.message.ingame.SkillOnCooldown;
import app.network.message.ingame.ShotOutOfStock;

@Component
public class SkillCastEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillCastEngine.class);

    private static final long TICK_INTERVAL_MS = 100L;
    // Wild Sweep (FRONT) : demi-angle du cône frappé devant le lanceur.
    private static final double FRONT_HALF_ANGLE = Math.PI / 3;
    // Peace : chance d'apaiser le monstre (DeleteHate chance=80 de L2J).
    private static final double PEACE_CHANCE = 0.80;
    // Shadow Step : distance d'arrivée derrière la cible.
    private static final double BLINK_BEHIND_DISTANCE = 1.2;

    private final Map<UUID, AbstractCharacter> casting = new ConcurrentHashMap<>();
    private final MovementEngine movementEngine;
    private final ProjectileEngine projectileEngine;
    private final EscapeEngine escapeEngine;
    private final SkillEffectApplier skillEffectApplier;
    private final SummonEngine summonEngine;
    private final ResurrectionEngine resurrectionEngine;
    private final MonsterAiEngine monsterAiEngine;
    private final CubicEngine cubicEngine;

    public SkillCastEngine(MovementEngine movementEngine, ProjectileEngine projectileEngine, EscapeEngine escapeEngine,
            SkillEffectApplier skillEffectApplier, @Lazy SummonEngine summonEngine,
            ResurrectionEngine resurrectionEngine, MonsterAiEngine monsterAiEngine, CubicEngine cubicEngine) {
        this.movementEngine = movementEngine;
        this.projectileEngine = projectileEngine;
        this.escapeEngine = escapeEngine;
        this.skillEffectApplier = skillEffectApplier;
        this.summonEngine = summonEngine;
        this.resurrectionEngine = resurrectionEngine;
        this.monsterAiEngine = monsterAiEngine;
        this.cubicEngine = cubicEngine;
    }

    @EventListener
    void onSkillCastBegin(SkillCastBegin event) {
        beginCast(event.caster(), event.activeSkill(), event.level(), event.target());
    }

    private void beginCast(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target) {
        // On incante sans marcher : le déplacement en cours est arrêté ici (avant même
        // le délai d'incantation) plutôt qu'à la résolution différée du sort
        // (resolveCast) — trop tard, le client continue sinon d'interpoler le
        // déplacement pendant toute la durée de l'incantation.
        movementEngine.stopMovement(caster);
        // Le lanceur fait face à sa cible dès le début de l'incantation (no-op sur soi,
        // voir MotionSystem.faceToward) ; diffusé via SkillCastStarted.casterHeading.
        caster.getMotionSystem().faceToward(target.getMotionSystem().getPosition());

        // Le soulshot/spiritshot est consommé au début du cast (pas à sa résolution) :
        // c'est le seul moment qui permet d'appliquer la réduction de temps
        // d'incantation du spiritshot à ActiveCast.castingTimeNanos, et de
        // déclencher l'animation client dès le lancement du sort plutôt qu'à sa
        // résolution, plusieurs secondes plus tard.
        boolean shotCharged = false;
        // ActiveSkill.castingTimeMs() est calibré pour un cast.spd neutre
        // (CombatFormulas.BASE_CAST_SPD) : effectiveCastingTimeMs() applique le ratio
        // L2J (BASE_CAST_SPD / cast.spd effectif) pour raccourcir/allonger
        // l'incantation réelle selon WIT (et les buffs/debuffs éventuels sur
        // ModifiedStat.CASTSPD, déjà couverts par StatSystem.getEffective) — vaut
        // aussi bien pour un joueur qu'un monstre lanceur de sort (tous deux passent
        // par CombatFormulas.baseStats(), voir PlayerInstance/MonsterCatalog).
        int castSpd = caster.getStatSystem().getEffective(ModifiedStat.CASTSPD);
        // Teleport (Scroll of Escape, Return) : durée fixe, ni cast.spd ni spiritshot.
        boolean teleport = activeSkill.skillType() == SkillEffectType.TELEPORT;
        long castingTimeNanos = teleport
                ? activeSkill.castingTimeMs() * 1_000_000L
                : CombatFormulas.effectiveCastingTimeMs(activeSkill.castingTimeMs(), castSpd) * 1_000_000L;
        ItemType shotType = shotTypeFor(activeSkill);
        if (shotType != null && caster instanceof PlayerInstance player) {
            ItemGrade activeGrade = shotType == ItemType.SOULSHOT
                    ? player.getInventorySystem().getActiveSoulshotGrade()
                    : player.getInventorySystem().getActiveSpiritshotGrade();
            if (activeGrade != null) {
                var outcome = player.getInventorySystem().consumeShot(shotType, activeGrade);
                if (outcome instanceof InventorySystem.ConsumeShotOutcome.OutOfStock) {
                    if (shotType == ItemType.SOULSHOT) {
                        player.getInventorySystem().setActiveSoulshotGrade(null);
                    } else {
                        player.getInventorySystem().setActiveSpiritshotGrade(null);
                    }
                    player.send(new ShotOutOfStock(shotType, activeGrade));
                    DomainEventPublisher.publish(new ShotGradeDepleted(player, shotType, activeGrade));
                } else {
                    shotCharged = true;
                    if (shotType == ItemType.SPIRITSHOT) {
                        castingTimeNanos = Math
                                .round(castingTimeNanos * (1 - CombatFormulas.SPIRITSHOT_CASTING_TIME_REDUCTION));
                    }
                }
            }
        }

        caster.getSkillSystem().updateCast(
                new ActiveCast(activeSkill, level, target, System.nanoTime(), castingTimeNanos, shotCharged));
        casting.put(caster.getId(), caster);
        log.debug("activeSkill.cast_started thread={} caster={} activeSkill={} castingTimeMs={} shotCharged={}",
                Thread.currentThread().getName(), caster.getId(), activeSkill.name(), castingTimeNanos / 1_000_000L,
                shotCharged);
        caster.broadcast(new SkillCastStarted(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                target.getId(), target.getName(), (int) (castingTimeNanos / 1_000_000L),
                caster.getMotionSystem().getHeading()), null);
    }

    // Charge consommée par un sort, comme dans L2 : soulshot pour une technique
    // physique offensive, spiritshot pour un sort magique (dégâts, soins,
    // buffs/debuffs, recharge) ; aucune pour Relax, les toggles, invocations,
    // téléportations, provocations...
    private static ItemType shotTypeFor(ActiveSkill activeSkill) {
        if (activeSkill.breakOnAction()) {
            return null;
        }
        return switch (activeSkill.skillType()) {
            case DAMAGE ->
                activeSkill.damageType() == SkillDamageType.PHYSICAL ? ItemType.SOULSHOT : ItemType.SPIRITSHOT;
            case HEALING, BUFF, DEBUFF, RECHARGE -> activeSkill.magic() ? ItemType.SPIRITSHOT : null;
            default -> null;
        };
    }

    public void cancelCast(AbstractCharacter caster) {
        ActiveCast activeCast = caster.getSkillSystem().getActiveCast();
        if (activeCast == null) {
            return;
        }
        caster.getSkillSystem().clearCast();
        casting.remove(caster.getId());
        log.debug("activeSkill.cast_cancelled thread={} caster={}", Thread.currentThread().getName(), caster.getId());
        caster.broadcast(new SkillCastCancelled(caster.getId(), caster.getName(), activeCast.activeSkill().id(),
                activeCast.activeSkill().name()), null);
    }

    // Un sort en cours d'incantation ne se résout pas après la déconnexion de son
    // lanceur.
    @EventListener
    void onPlayerRemovedFromWorld(PlayerRemovedFromWorld event) {
        if (casting.remove(event.character().getId()) != null) {
            event.character().getSkillSystem().clearCast();
        }
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    void tick() {
        long now = System.nanoTime();
        for (AbstractCharacter caster : casting.values()) {
            ActiveCast activeCast = caster.getSkillSystem().getActiveCast();
            if (activeCast == null) {
                casting.remove(caster.getId());
                continue;
            }
            if (now - activeCast.startedAtNanos() < activeCast.castingTimeNanos()) {
                continue;
            }
            casting.remove(caster.getId());
            caster.getSkillSystem().clearCast();
            try {
                resolveCast(caster, activeCast);
            } catch (Exception e) {
                log.error("activeSkill.cast_resolution_failed caster={}", caster.getId(), e);
            }
        }
    }

    private void resolveCast(AbstractCharacter caster, ActiveCast activeCast) {
        ActiveSkill activeSkill = activeCast.activeSkill();
        int level = activeCast.level();
        AbstractCharacter primaryTarget = activeCast.target();

        if (caster.getResourceSystem().getCurrentHealth() <= 0) {
            return;
        }
        if (activeSkill.skillType() == SkillEffectType.TELEPORT) {
            // Return (appris) coûte des PM ; le Scroll of Escape est gratuit.
            if (caster.getSkillSystem().knows(activeSkill.id())) {
                if (!caster.getResourceSystem().trySpendMana(activeSkill.manaCostAt(level))) {
                    caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Plus assez de mana."));
                    return;
                }
                caster.getSkillSystem().markCooldown(activeSkill);
            }
            escapeEngine.begin(caster);
            return;
        }
        if (!isTargetStillValid(caster, activeSkill, primaryTarget)) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "La cible n'est plus valide."));
            return;
        }
        if (caster.getResourceSystem().getCurrentMana() < activeSkill.manaCostAt(level)) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Plus assez de mana."));
            return;
        }
        if (!caster.getResourceSystem().trySpendHealth(activeSkill.hpCostAt(level))) {
            caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Plus assez de PV."));
            return;
        }
        caster.getResourceSystem().trySpendMana(activeSkill.manaCostAt(level));

        // Passé ce point le sort va jusqu'au jet (hit ou miss) : le cooldown
        // s'applique dans tous les cas, il faut donc en informer le client ici.
        caster.send(new SkillOnCooldown(activeSkill.name(), activeSkill.reuseTimeMs(), false));

        if (activeSkill.skillType() == SkillEffectType.DAMAGE && activeSkill.projectile()) {
            SkillSystem.AttackRollOutcome roll = caster.getSkillSystem().rollDamage(activeSkill, level, primaryTarget,
                    activeCast.shotCharged());
            caster.getSkillSystem().markCooldown(activeSkill);
            DomainEventPublisher.publish(new SkillCast(caster, activeSkill, level, primaryTarget, roll.amount(), false,
                    roll.hit(), null, List.of()));
            projectileEngine.launch(caster, activeSkill, level, primaryTarget, roll);
            return;
        }

        List<AbstractCharacter> targets = resolveTargets(caster, activeSkill, primaryTarget);
        switch (activeSkill.skillType()) {
            case SUMMON, CUBIC, RESURRECT, AGGRO, PEACE, BLINK -> {
                caster.getSkillSystem().markCooldown(activeSkill);
                resolveSpecial(caster, activeSkill, level, primaryTarget, targets);
                return;
            }
            default -> {
            }
        }
        if (activeSkill.target() == SkillTargetType.CORPSE) {
            // Corpse Life Drain : le cadavre le plus proche est consommé, le lanceur
            // soigné.
            MapInstance map = caster.getMotionSystem().getCurrentMap();
            Optional<Corpse> corpse = map.nearestCorpse(caster.getMotionSystem().getPosition(), activeSkill.range());
            if (corpse.isEmpty() || !map.consumeCorpse(corpse.get())) {
                caster.send(new SkillFizzled(activeSkill.id(), activeSkill.name(), "Aucun cadavre à portée."));
                return;
            }
        }

        // cast(...) marque le cooldown à chaque appel (une fois par cible touchée en
        // AOE) : les timestamps successifs sont assez proches pour ne pas dériver.
        boolean anyHit = false;
        for (AbstractCharacter target : targets) {
            if (target != primaryTarget && !isTargetStillValid(caster, activeSkill, target)) {
                continue;
            }
            anyHit |= resolveCastOnTarget(caster, activeSkill, level, target, activeCast.shotCharged());
        }
        if (targets.isEmpty()) {
            caster.getSkillSystem().markCooldown(activeSkill);
        }
        if (!anyHit && targets.size() > 1) {
            log.debug("activeSkill.aoe_no_target_hit caster={} activeSkill={}", caster.getId(), activeSkill.name());
        }
    }

    // Invocations, cubiques, résurrections, provocations, apaisement, pas de
    // l'ombre : délégués aux moteurs concernés puis annoncés comme un sort
    // ordinaire (animation, journal).
    private void resolveSpecial(AbstractCharacter caster, ActiveSkill activeSkill, int level,
            AbstractCharacter primaryTarget, List<AbstractCharacter> targets) {
        switch (activeSkill.skillType()) {
            case SUMMON -> {
                boolean summoned = caster instanceof PlayerInstance player
                        && summonEngine.summon(player, activeSkill, level);
                announce(caster, activeSkill, level, caster, summoned, 0);
            }
            case CUBIC -> {
                for (AbstractCharacter target : targets) {
                    cubicEngine.grant(caster, target, activeSkill);
                    announce(caster, activeSkill, level, target, true, 0);
                }
            }
            case RESURRECT -> {
                for (AbstractCharacter target : targets) {
                    if (target instanceof PlayerInstance dead && caster instanceof PlayerInstance player) {
                        resurrectionEngine.offer(player, dead, activeSkill, level);
                        announce(caster, activeSkill, level, target, true, 0);
                    }
                }
            }
            case AGGRO -> {
                for (AbstractCharacter target : targets) {
                    if (target instanceof MonsterInstance monster) {
                        monsterAiEngine.aggro(monster, caster);
                        announce(caster, activeSkill, level, target, true, 0);
                    }
                }
            }
            case PEACE -> {
                boolean calmed = primaryTarget instanceof MonsterInstance monster && Randomizer.rollChance(PEACE_CHANCE)
                        && monsterAiEngine.calm(monster);
                announce(caster, activeSkill, level, primaryTarget, calmed, 0);
            }
            case BLINK -> {
                blinkBehind(caster, primaryTarget);
                announce(caster, activeSkill, level, primaryTarget, true, 0);
            }
            default -> throw new IllegalStateException("Type non spécial : " + activeSkill.skillType());
        }
    }

    // Shadow Step : le lanceur apparaît dans le dos de sa cible, face à elle ;
    // les monstres qui le poursuivaient perdent sa trace (TargetCancel L2J).
    private void blinkBehind(AbstractCharacter caster, AbstractCharacter target) {
        Position targetPosition = target.getMotionSystem().getPosition();
        double heading = target.getMotionSystem().getHeading();
        Position behind = new Position(targetPosition.x() - Math.cos(heading) * BLINK_BEHIND_DISTANCE,
                targetPosition.y() - Math.sin(heading) * BLINK_BEHIND_DISTANCE);
        MapInstance map = caster.getMotionSystem().getCurrentMap();
        Position arrival = map.isWalkable(behind) ? behind : targetPosition;
        movementEngine.stopMovement(caster);
        caster.getMotionSystem().setPosition(arrival);
        caster.getMotionSystem().faceToward(targetPosition);
        caster.getKnownList().refresh();
        monsterAiEngine.dropTarget(caster, target);
        caster.broadcast(new CharacterBlinked(caster.getId(), caster.getName(), arrival.x(), arrival.y(),
                caster.getMotionSystem().getHeading()), null);
    }

    private void announce(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target,
            boolean hit, int amount) {
        caster.send(new CastResult(activeSkill.id(), activeSkill.name(), target.getId(), target.getName(),
                target == caster, hit, amount, target.getResourceSystem().getCurrentHealth(),
                target.getResourceSystem().getMaxHealth(), false, activeSkill.manaCostAt(level),
                caster.getResourceSystem().getCurrentMana(), caster.getResourceSystem().getMaxMana()));
        caster.broadcast(new SkillCastAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                target.getId(), target.getName(), target == caster, hit, amount,
                target.getResourceSystem().getCurrentHealth(), target.getResourceSystem().getMaxHealth(), false),
                caster instanceof PlayerInstance player ? player : null);
    }

    // Applique le sort sur UNE cible (mono-cible, ou une cible d'un groupe AOE) et
    // diffuse les messages réseau correspondants. Retourne si la cible a
    // effectivement été touchée.
    private boolean resolveCastOnTarget(AbstractCharacter caster, ActiveSkill activeSkill, int level,
            AbstractCharacter target, boolean shotCharged) {
        SkillSystem.CastOutcome outcome = caster.getSkillSystem().cast(activeSkill, level, target, shotCharged);
        DomainEventPublisher.publish(new SkillCast(caster, activeSkill, level, target, outcome.amount(),
                outcome.targetDefeated(), outcome.hit(), outcome.effectExpiresAt(), outcome.modifiers()));
        if (outcome.hit()) {
            skillEffectApplier.applySecondaryEffects(caster, activeSkill, level, target);
        }
        if (outcome.drained() > 0) {
            caster.broadcast(new SkillDrained(caster.getId(), caster.getName(), target.getId(), activeSkill.name(),
                    outcome.drained(), caster.getResourceSystem().getCurrentHealth(),
                    caster.getResourceSystem().getMaxHealth()), null);
        }

        if (activeSkill.skillType() == SkillEffectType.BUFF || activeSkill.skillType() == SkillEffectType.DEBUFF
                || activeSkill.skillType() == SkillEffectType.TOGGLE) {
            boolean beneficial = activeSkill.skillType() != SkillEffectType.DEBUFF;
            int durationSeconds = activeSkill.effects().isEmpty() ? 0 : activeSkill.effects().get(0).time();
            caster.broadcast(
                    new SkillModifierAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                            target.getId(), target.getName(), target == caster, beneficial, outcome.hit(),
                            outcome.modifiers(), outcome.amount(), durationSeconds, activeSkill.manaCostAt(level),
                            caster.getResourceSystem().getCurrentMana(), caster.getResourceSystem().getMaxMana()),
                    null);
            return outcome.hit();
        }

        caster.send(new CastResult(activeSkill.id(), activeSkill.name(), target.getId(), target.getName(),
                outcome.selfHeal(), outcome.hit(), outcome.amount(), outcome.targetHealthAfter(),
                outcome.targetMaxHealth(), outcome.targetDefeated(), activeSkill.manaCostAt(level),
                caster.getResourceSystem().getCurrentMana(), caster.getResourceSystem().getMaxMana()));
        caster.broadcast(
                new SkillCastAnnounced(caster.getId(), caster.getName(), activeSkill.id(), activeSkill.name(),
                        target.getId(), target.getName(), outcome.selfHeal(), outcome.hit(), outcome.amount(),
                        outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()),
                caster instanceof PlayerInstance player ? player : null);
        if (outcome.targetDefeated()) {
            caster.clearCombatTarget();
        }
        return outcome.hit();
    }

    private List<AbstractCharacter> resolveTargets(AbstractCharacter caster, ActiveSkill activeSkill,
            AbstractCharacter primaryTarget) {
        return switch (activeSkill.target()) {
            case AOE -> resolveAoeTargets(caster, activeSkill, primaryTarget);
            case AURA -> hostilesAround(caster, caster.getMotionSystem().getPosition(), radiusOf(activeSkill));
            case FRONT -> resolveFrontTargets(caster, activeSkill);
            case PARTY -> resolvePartyTargets(caster, activeSkill, false);
            case DEAD_PARTY -> resolvePartyTargets(caster, activeSkill, true);
            case ONE, SELF, SERVITOR, CORPSE, DEAD_PLAYER -> List.of(primaryTarget);
        };
    }

    private static double radiusOf(ActiveSkill activeSkill) {
        return activeSkill.aoeRadius() > 0 ? activeSkill.aoeRadius() : activeSkill.range();
    }

    // Zone autour de la cible : la cible elle-même (même un joueur, PvP) et les
    // monstres autour d'elle pour un sort offensif ; tous les occupants sinon.
    private List<AbstractCharacter> resolveAoeTargets(AbstractCharacter caster, ActiveSkill activeSkill,
            AbstractCharacter primaryTarget) {
        Position center = primaryTarget.getMotionSystem().getPosition();
        if (!activeSkill.harmful()) {
            return caster.getMotionSystem().getCurrentMap().occupantsWithin(center, radiusOf(activeSkill));
        }
        List<AbstractCharacter> targets = new ArrayList<>();
        targets.add(primaryTarget);
        hostilesAround(caster, center, radiusOf(activeSkill)).stream().filter(other -> other != primaryTarget)
                .forEach(targets::add);
        return targets;
    }

    // Monstres vivants dans le rayon : jamais le lanceur, son serviteur, un PNJ
    // ou un autre joueur (pas de PvP de zone).
    private static List<AbstractCharacter> hostilesAround(AbstractCharacter caster, Position center, double radius) {
        return caster.getMotionSystem().getCurrentMap().occupantsWithin(center, radius).stream()
                .filter(other -> other instanceof MonsterInstance && other != caster
                        && other.getResourceSystem().getCurrentHealth() > 0)
                .toList();
    }

    // Wild Sweep : les monstres dans un cône devant le lanceur.
    private static List<AbstractCharacter> resolveFrontTargets(AbstractCharacter caster, ActiveSkill activeSkill) {
        Position origin = caster.getMotionSystem().getPosition();
        double heading = caster.getMotionSystem().getHeading();
        double reach = activeSkill.range() + radiusOf(activeSkill);
        return hostilesAround(caster, origin, reach).stream().filter(other -> {
            double toOther = origin.headingTo(other.getMotionSystem().getPosition());
            return Math.abs(Math.IEEEremainder(toOther - heading, 2 * Math.PI)) <= FRONT_HALF_ANGLE;
        }).toList();
    }

    // Group Heal : le lanceur et les membres vivants de son groupe présents sur
    // la même carte dans le rayon du sort (aoeRadius, à défaut range) autour de
    // lui — le lanceur seul hors groupe. Mass Resurrection (dead) : les membres
    // morts seulement.
    private List<AbstractCharacter> resolvePartyTargets(AbstractCharacter caster, ActiveSkill activeSkill,
            boolean dead) {
        if (!(caster instanceof PlayerInstance player) || player.getPartySystem().getParty() == null) {
            return dead ? List.of() : List.of(caster);
        }
        double radius = radiusOf(activeSkill);
        Position center = caster.getMotionSystem().getPosition();
        List<AbstractCharacter> targets = new ArrayList<>();
        if (!dead) {
            targets.add(caster);
        }
        for (PlayerInstance member : player.getPartySystem().getParty().getMembers()) {
            boolean alive = member.getResourceSystem().getCurrentHealth() > 0;
            if (member != caster && alive != dead && caster.getMotionSystem().getCurrentMap().isPresent(member)
                    && member.getMotionSystem().getPosition().distanceTo(center) <= radius) {
                targets.add(member);
            }
        }
        return targets;
    }

    private boolean isTargetStillValid(AbstractCharacter caster, ActiveSkill activeSkill, AbstractCharacter target) {
        if (target == caster) {
            return true;
        }
        if (target == null || !caster.getMotionSystem().getCurrentMap().isPresent(target)) {
            return false;
        }
        boolean wantsDead = activeSkill.target() == SkillTargetType.DEAD_PLAYER
                || activeSkill.target() == SkillTargetType.DEAD_PARTY;
        if (wantsDead != (target.getResourceSystem().getCurrentHealth() <= 0)) {
            return false;
        }
        if (target instanceof AbstractNpc && activeSkill.harmful()) {
            return false;
        }
        if (target instanceof SummonInstance summon && summon.getOwner() == caster && activeSkill.harmful()) {
            return false;
        }
        if (target.getCombatSystem().isTeleporting() && activeSkill.harmful()) {
            return false;
        }
        if (activeSkill.target().selfCentered()) {
            return true;
        }
        return activeSkill.range() <= 0 || caster.getMotionSystem().getPosition()
                .distanceTo(target.getMotionSystem().getPosition()) <= activeSkill.range();
    }

    public record ActiveCast(ActiveSkill activeSkill, int level, AbstractCharacter target, long startedAtNanos,
            long castingTimeNanos, boolean shotCharged) {
    }
}

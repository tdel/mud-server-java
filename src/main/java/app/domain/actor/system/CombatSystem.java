package app.domain.actor.system;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterBeginAttack;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterDied;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.ShotGradeDepleted;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.instance.SummonInstance;
import app.domain.item.WeaponType;
import app.game.combat.DamageModifiers;
import app.domain.item.ItemGrade;
import app.domain.item.ItemType;
import app.domain.world.PeaceZone;
import app.game.combat.CombatFormulas;
import app.game.Randomizer;
import app.network.message.ingame.AttackResult;

public final class CombatSystem {

    private static final Logger log = LoggerFactory.getLogger(CombatSystem.class);

    // Portée de base de l'arc (500 unités L2 / 20).
    public static final double BOW_ATTACK_RANGE = 25.0;

    private final AbstractCharacter character;
    private final boolean invulnerable;
    private volatile AbstractCharacter target;
    private volatile Instant nextAttackAt = Instant.MIN;
    // Entre la fin de l'incantation d'un Scroll of Escape et la téléportation
    // effective (cf. EscapeEngine) : intouchable, alors qu'il reste attaquable
    // pendant l'incantation elle-même.
    private volatile boolean teleporting;

    public CombatSystem(AbstractCharacter character, boolean invulnerable) {
        this.character = character;
        this.invulnerable = invulnerable;
    }

    public boolean isTeleporting() {
        return teleporting;
    }

    public void setTeleporting(boolean teleporting) {
        this.teleporting = teleporting;
    }

    public AbstractCharacter getTarget() {
        return target;
    }

    public void setTarget(AbstractCharacter target) {
        this.target = target;
    }

    public void clearTarget() {
        this.target = null;
    }

    public boolean isReady() {
        return !Instant.now().isBefore(nextAttackAt);
    }

    public Duration remainingCooldown() {
        Duration remaining = Duration.between(Instant.now(), nextAttackAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public AttackOutcome attack(AbstractCharacter defender) {
        if (defender == null) {
            return new AttackOutcome.NoTarget();
        }
        if (defender.getResourceSystem().getCurrentHealth() <= 0) {
            log.debug("attack.rejected character={} reason=target_dead target={}", character.getId(), defender.getId());
            setTarget(null);
            return new AttackOutcome.TargetInvalid(defender.getId());
        }
        // Les serviteurs invoqués ne sont attaquables que par les monstres.
        if (defender instanceof AbstractNpc || defender.getCombatSystem().isTeleporting()
                || defender instanceof SummonInstance && character instanceof PlayerInstance) {
            log.debug("attack.rejected character={} reason=target_not_attackable target={}", character.getId(),
                    defender.getId());
            return new AttackOutcome.TargetInvalid(defender.getId());
        }
        PeaceZone peaceZone = character.getZone() instanceof PeaceZone attackerZone
                ? attackerZone
                : defender.getZone() instanceof PeaceZone defenderZone ? defenderZone : null;
        if (peaceZone != null) {
            log.debug("attack.rejected character={} reason=peace_zone zone={} target={}", character.getId(),
                    peaceZone.getName(), defender.getId());
            return new AttackOutcome.ForbiddenZone(peaceZone.getName());
        }
        if (character.getEffectsSystem().isIncapacitated()) {
            return new AttackOutcome.Incapacitated();
        }
        if (character.getMotionSystem().getPosition()
                .distanceTo(defender.getMotionSystem().getPosition()) > attackRange()) {
            log.debug("attack.rejected character={} reason=out_of_range target={}", character.getId(),
                    defender.getId());
            return new AttackOutcome.OutOfRange(defender.getName());
        }
        if (!isReady()) {
            log.debug("attack.rejected character={} reason=on_cooldown target={}", character.getId(), defender.getId());
            return new AttackOutcome.OnCooldown(remainingCooldown().toMillis());
        }

        DomainEventPublisher.publish(new CharacterBeginAttack(character, defender, true));
        // L'attaquant fait face à sa cible — après CharacterBeginAttack, qui arrête son
        // déplacement (MovementEngine), pour qu'un pas en cours ne réécrive pas le
        // heading. Diffusé dans AttackResult (le client n'a pas d'autre moyen de
        // l'apprendre à l'arrêt) et repris par EntityView pour les nouveaux arrivants.
        character.getMotionSystem().faceToward(defender.getMotionSystem().getPosition());

        // Le soulshot est consommé à chaque tentative d'attaque (hit ou miss), comme
        // en L2J — d'où sa place ici, avant le jet de touche, une fois l'attaque
        // garantie de se produire (toutes les guards ci-dessus déjà passées).
        boolean shotCharged = false;
        if (character instanceof PlayerInstance player
                && player.getInventorySystem().getActiveSoulshotGrade() != null) {
            ItemGrade grade = player.getInventorySystem().getActiveSoulshotGrade();
            var outcome = player.getInventorySystem().consumeShot(ItemType.SOULSHOT, grade);
            if (outcome instanceof InventorySystem.ConsumeShotOutcome.OutOfStock) {
                DomainEventPublisher.publish(new ShotGradeDepleted(player, ItemType.SOULSHOT, grade));
            } else {
                shotCharged = true;
            }
        }

        double hitChance = CombatFormulas.hitChance(character.getStatSystem().getEffective(ModifiedStat.ACCURACY),
                defender.getStatSystem().getEffective(ModifiedStat.EVASION));
        boolean hit = Randomizer.rollChance(hitChance);

        int damage = 0;
        boolean critical = false;
        boolean defeated = false;
        int healthAfter = defender.getResourceSystem().getCurrentHealth();

        if (hit) {
            critical = Randomizer.rollChance(DamageModifiers.physicalCritChance(character, defender));
            damage = CombatFormulas.resolvePhysicalDamage(character.getStatSystem().getEffective(ModifiedStat.PATK),
                    DamageModifiers.defenseWithBlock(defender), critical, shotCharged);
            damage = DamageModifiers.applyPhysical(character, defender, damage, critical);
            defeated = applyDamage(defender, damage);
            healthAfter = defender.getResourceSystem().getCurrentHealth();
            // Reflect Damage / Blazing Skin : une part des coups de mêlée revient à
            // l'attaquant.
            int reflected = character.getSkillSystem().wieldedWeapon() == WeaponType.BOW
                    ? 0
                    : DamageModifiers.reflected(defender, damage);
            if (reflected > 0 && !defeated) {
                character.takeDamage(reflected, defender);
            }
        }

        Duration cooldown = CombatFormulas.attackCooldown(character.getStatSystem().getEffective(ModifiedStat.ATKSPD));
        nextAttackAt = Instant.now().plus(cooldown);

        log.info(
                "combat.attack_resolved attacker={} defender={} hit={} critical={} damage={} defenderHealthAfter={} defeated={}",
                character.getId(), defender.getId(), hit, critical, damage, healthAfter, defeated);

        character.broadcast(new AttackResult(character.getId(), character.getName(), defender.getId(),
                defender.getName(), hit, critical, damage, healthAfter, character.getMotionSystem().getHeading()),
                null);
        return new AttackOutcome.Success(cooldown.toMillis());
    }

    // Portée de l'attaque normale : corps à corps, ou portée de l'arc (500 en L2,
    // soit 25 cases) allongée par Long Shot (ATTACK_RANGE).
    public double attackRange() {
        double base = character.getSkillSystem().wieldedWeapon() == WeaponType.BOW
                ? BOW_ATTACK_RANGE
                : CombatFormulas.ATTACK_RANGE;
        return base + character.getStatSystem().adjust(ModifiedStat.ATTACK_RANGE, 0);
    }

    private boolean applyDamage(AbstractCharacter defender, int damage) {
        return defender.takeDamage(damage, character);
    }

    // Logique commune à PlayerInstance et MonsterInstance :
    // synchronized(character)
    // couvre le cas monstre (attaqué par un joueur en même temps que
    // MonsterAiEngine
    // le fait agir), et ne coûte rien de plus pour un joueur (déjà mono-thread).
    public boolean takeDamage(int amount, AbstractCharacter attacker) {
        if (invulnerable || teleporting) {
            return false;
        }
        // Transfer Pain : une part des dégâts subis par un joueur passe à son
        // serviteur tant que celui-ci tient debout.
        if (character instanceof PlayerInstance player && player.getSummon() != null
                && attacker != player.getSummon()) {
            int percent = character.getStatSystem().adjust(ModifiedStat.TRANSFER_DAMAGE, 0);
            SummonInstance summon = player.getSummon();
            if (percent > 0 && summon.getResourceSystem().getCurrentHealth() > 1) {
                int transferred = Math.min(amount * percent / 100, summon.getResourceSystem().getCurrentHealth() - 1);
                if (transferred > 0) {
                    summon.takeDamage(transferred, attacker);
                    amount -= transferred;
                }
            }
        }
        boolean defeated;
        synchronized (character) {
            if (character.getResourceSystem().getCurrentHealth() <= 0 || character.getZone() instanceof PeaceZone) {
                return false;
            }
            character.getResourceSystem()
                    .setCurrentHealth(Math.max(0, character.getResourceSystem().getCurrentHealth() - amount));
            defeated = character.getResourceSystem().getCurrentHealth() <= 0;
        }
        log.debug("character.take_damage thread={} character={} attacker={} amount={} healthAfter={}",
                Thread.currentThread().getName(), character.getId(), attacker.getId(), amount,
                character.getResourceSystem().getCurrentHealth());
        DomainEventPublisher.publish(new CharacterDamaged(character, attacker, amount));
        if (defeated) {
            clearTarget();
            DomainEventPublisher.publish(new CharacterDied(character, attacker));
        }
        return defeated;
    }

    public sealed interface AttackOutcome {

        record Success(long cooldownMs) implements AttackOutcome {
        }

        record NoTarget() implements AttackOutcome {
        }

        record TargetInvalid(UUID targetId) implements AttackOutcome {
        }

        record ForbiddenZone(String zoneName) implements AttackOutcome {
        }

        record OutOfRange(String targetName) implements AttackOutcome {
        }

        record OnCooldown(long remainingMs) implements AttackOutcome {
        }

        // Étourdi, endormi, paralysé, terrorisé ou en mort feinte.
        record Incapacitated() implements AttackOutcome {
        }
    }
}

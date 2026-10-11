package app.game.engine;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.domain.AbnormalStatus;
import app.domain.ActiveEffect;
import app.domain.SkillEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterDamaged;
import app.domain.actor.event.CharacterEffectApplied;
import app.domain.actor.event.CharacterEffectExpired;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.domain.map.GridPathfinder;
import app.domain.map.Position;
import app.game.Randomizer;
import app.network.message.ingame.CharacterMovementStarted;
import app.network.message.ingame.MovementStarted;

// Conséquences immédiates des altérations d'état (AbnormalStatus) et des coups
// reçus : un personnage étourdi/endormi/paralysé s'arrête et perd son
// incantation, un enraciné s'arrête ; un joueur terrorisé s'enfuit ; la mort
// feinte fait oublier le joueur aux monstres. Un coup reçu réveille un dormeur
// et peut interrompre une incantation magique (calcAtkBreak L2J, atténué par
// la MEN et par Concentration).
@Component
public class StatusEngine {

    private static final Logger log = LoggerFactory.getLogger(StatusEngine.class);

    // Fuite d'un joueur terrorisé : distance parcourue à l'opposé de sa cible.
    private static final double FEAR_RUN_DISTANCE = 6.0;

    private final MovementEngine movementEngine;
    private final SkillCastEngine skillCastEngine;
    private final MonsterAiEngine monsterAiEngine;

    public StatusEngine(MovementEngine movementEngine, SkillCastEngine skillCastEngine,
            MonsterAiEngine monsterAiEngine) {
        this.movementEngine = movementEngine;
        this.skillCastEngine = skillCastEngine;
        this.monsterAiEngine = monsterAiEngine;
    }

    @EventListener
    void onCharacterEffectApplied(CharacterEffectApplied event) {
        AbnormalStatus status = event.effect().status();
        if (status == null) {
            return;
        }
        AbstractCharacter character = event.character();
        if (status.blocksMovement() && status != AbnormalStatus.FEAR) {
            movementEngine.stopMovement(character);
        }
        if (status.incapacitates()) {
            skillCastEngine.cancelCast(character);
            character.getCombatSystem().clearTarget();
        }
        if (status == AbnormalStatus.FAKE_DEATH) {
            monsterAiEngine.dropTarget(character, null);
        }
        if (status == AbnormalStatus.FEAR && character instanceof PlayerInstance player) {
            runAway(player);
        }
        log.debug("status.applied character={} status={}", character.getId(), status);
    }

    // Joueur terrorisé : il court à l'opposé de sa cible (ou droit devant lui).
    private void runAway(PlayerInstance player) {
        Position position = player.getMotionSystem().getPosition();
        AbstractCharacter threat = player.getCombatSystem().getTarget();
        Position away = threat != null && threat.getMotionSystem().getPosition() != null
                ? position.minus(threat.getMotionSystem().getPosition()).normalized()
                : new Position(Math.cos(player.getMotionSystem().getHeading()),
                        Math.sin(player.getMotionSystem().getHeading()));
        Position destination = position.plus(away.scaled(FEAR_RUN_DISTANCE));
        Optional<List<Position>> path = GridPathfinder.findPath(position, destination,
                player.getMotionSystem().getCurrentMap().getCollisionGrid());
        if (path.isEmpty() || path.get().isEmpty()) {
            return;
        }
        movementEngine.startMovement(path.get(), player);
        player.send(new MovementStarted(destination.x(), destination.y(), player.getMotionSystem().getHeading(),
                position.x(), position.y(), path.get()));
        player.broadcast(new CharacterMovementStarted(player.getId(), player.getName(), position.x(), position.y(),
                destination.x(), destination.y(), player.getMotionSystem().getHeading(), path.get()), player);
    }

    // Fin d'une entrave : le monstre reprend sa poursuite au prochain tick d'IA ;
    // rien à faire ici hormis la journalisation.
    @EventListener
    void onCharacterEffectExpired(CharacterEffectExpired event) {
        if (event.effect().status() != null) {
            log.debug("status.expired character={} status={}", event.character().getId(), event.effect().status());
        }
    }

    @EventListener
    void onCharacterDamaged(CharacterDamaged event) {
        AbstractCharacter character = event.character();
        if (character.getResourceSystem().getCurrentHealth() <= 0) {
            return;
        }
        // Sleep : le moindre dégât réveille (removedOnDamage L2J).
        for (ActiveEffect woken : character.getEffectsSystem().removeStatus(AbnormalStatus.SLEEP)) {
            DomainEventPublisher.publish(new CharacterEffectExpired(character, woken));
        }
        SkillCastEngine.ActiveCast cast = character.getSkillSystem().getActiveCast();
        // Un parchemin/retour en ville (TELEPORT) n'est pas interrompu par les coups.
        if (cast != null && cast.activeSkill().magic() && cast.activeSkill().skillType() != SkillEffectType.TELEPORT
                && Randomizer.rollChance(breakChance(character, event.amount()))) {
            log.debug("status.cast_interrupted character={} damage={}", character.getId(), event.amount());
            skillCastEngine.cancelCast(character);
        }
    }

    // calcAtkBreak L2J : 15 + sqrt(13 x dégâts) %, moins le bonus de MEN, plus
    // CAST_CANCEL (négatif sous Concentration), borné à [1, 99] %.
    static double breakChance(AbstractCharacter character, int damage) {
        double menBonus = (Attribute.MEN.bonus(character.getAttributeSystem().getAttribute(Attribute.MEN)) - 1) * 100;
        double chance = 15 + Math.sqrt(13.0 * damage) - menBonus
                + character.getStatSystem().adjust(ModifiedStat.CAST_CANCEL, 0);
        return Math.clamp(chance, 1, 99) / 100.0;
    }
}

package app.network.command.ingame;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.SpellEffectType;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.AbstractNpc;
import app.domain.actor.component.SpellCasting;
import app.domain.actor.instance.CharacterInstance;
import app.game.engine.MovementEngine;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.CastReceived;
import app.network.message.ingame.CastResult;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MovementStopped;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotEnoughMana;
import app.network.message.ingame.SpellModifierAnnounced;
import app.network.message.ingame.SpellCastObserved;
import app.network.message.ingame.SpellNotKnown;
import app.network.message.ingame.SpellOnCooldown;
import app.network.message.ingame.SpellOutOfRange;
import app.network.message.ingame.TargetNotFound;

@Component
public class Cast implements CommandHandler {

    private final MovementEngine movementEngine;

    public Cast(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

    @Override
    public String name() {
        return "cast";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String trimmed = argument.trim();

        if (trimmed.isEmpty()) {
            connection.send(new Usage("cast <spell> [target]"));
            return;
        }

        Optional<Spell> resolved = resolveKnownSpell(character, trimmed);
        if (resolved.isEmpty()) {
            connection.send(new SpellNotKnown(trimmed));
            return;
        }
        Spell spell = resolved.get();
        String targetName = trimmed.substring(spell.name().length()).trim();

        boolean selfTargetedByDefault = spell.effect() == SpellEffectType.HEALING
                || spell.effect() == SpellEffectType.BUFF;

        AbstractCharacter target;
        if (selfTargetedByDefault && targetName.isEmpty()) {
            target = character;
        } else if (!targetName.isEmpty()) {
            Optional<AbstractCharacter> found = character.getCurrentZone().findAttackableByName(targetName, character);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(targetName));
                return;
            }
            target = found.get();
        } else {
            target = character.getCombat().getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
        }

        // "select" (voir Select.java) permet aussi de cibler un PNJ (interaction, pas
        // combat) : un sort à dégâts lancé sans nom explicite sur un PNJ sélectionné
        // finirait ici avec une cible non attaquable (SpellCasting.applyDamage ne gère
        // que MonsterInstance/CharacterInstance) ; BUFF/DEBUFF n'ont pas ce problème
        // (ils ne font qu'ajouter un effet actif) mais autant rester cohérent.
        if (target instanceof AbstractNpc && spell.effect() == SpellEffectType.DAMAGE) {
            connection.send(new TargetNotFound(target.getName()));
            return;
        }

        if (spell.range() > 0 && character.getPosition().distanceTo(target.getPosition()) > spell.range()) {
            connection.send(new SpellOutOfRange(spell.name(), target.getName()));
            return;
        }

        SpellCasting spellCasting = character.getSpellCasting();
        if (!spellCasting.isReady(spell.id())) {
            connection.send(new SpellOnCooldown(spell.name(), spellCasting.remainingCooldown(spell.id()).toMillis()));
            return;
        }

        if (character.getCurrentMana() < spell.manaCost()) {
            connection.send(new NotEnoughMana(spell.name(), spell.manaCost(), character.getCurrentMana()));
            return;
        }

        // Toutes les vérifications (sort connu, cible, portée, cooldown, mana) sont
        // passées : le sort va effectivement être lancé, on arrête donc le déplacement en
        // cours juste avant de le calculer — on ne combat pas en marchant.
        if (character.activeMovement != null) {
            movementEngine.stopMovement(character);
            connection.send(new MovementStopped(character.getPosition().x(), character.getPosition().y()));
            character.getCurrentZone().broadcast(
                    new CharacterMovementStopped(character.getName(), character.getPosition().x(),
                            character.getPosition().y()),
                    character);
        }

        SpellCasting.CastOutcome outcome = character.castSpell(spell, target);

        if (spell.effect() == SpellEffectType.BUFF || spell.effect() == SpellEffectType.DEBUFF) {
            boolean beneficial = spell.effect() == SpellEffectType.BUFF;
            character.getCurrentZone().broadcast(
                    new SpellModifierAnnounced(character.getName(), spell.name(), target.getName(), target == character,
                            beneficial, outcome.hit(), spell.modifiedStat(), outcome.amount(), spell.durationSeconds()),
                    null);
            return;
        }

        connection.send(new CastResult(spell.name(), target.getName(), outcome.selfHeal(), outcome.hit(),
                outcome.amount(), outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        if (target != character) {
            target.send(new CastReceived(character.getName(), spell.name(), outcome.hit(), outcome.amount(),
                    outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        }
        character.getCurrentZone().broadcast(new SpellCastObserved(character.getName(), spell.name(), target.getName(),
                outcome.selfHeal(), outcome.hit(), outcome.amount(), outcome.targetDefeated()), null);
        if (outcome.targetDefeated()) {
            character.getCombat().setTarget(null);
        }
    }

    private Optional<Spell> resolveKnownSpell(CharacterInstance character, String argument) {
        String lower = argument.toLowerCase();
        Stream<Spell> known = character.getSpellCasting().knownSpells().stream();
        Stream<Spell> granted = character.getGrantedSpells().stream();
        return Stream.concat(known, granted).distinct().filter(spell -> lower.startsWith(spell.name().toLowerCase()))
                .max(Comparator.comparingInt(spell -> spell.name().length()));
    }
}

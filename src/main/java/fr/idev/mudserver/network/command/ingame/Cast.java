package fr.idev.mudserver.network.command.ingame;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.SpellEffectType;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.SpellCasting;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.catalog.SpellCatalog;
import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CastReceived;
import fr.idev.mudserver.network.message.ingame.CastResult;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.NotEnoughMana;
import fr.idev.mudserver.network.message.ingame.SpellNotKnown;
import fr.idev.mudserver.network.message.ingame.SpellOnCooldown;
import fr.idev.mudserver.network.message.ingame.SpellOutOfRange;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Cast implements CommandHandler {

    private final SpellCatalog spellCatalog;

    public Cast(SpellCatalog spellCatalog) {
        this.spellCatalog = spellCatalog;
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

        AbstractCharacter target;
        if (spell.effect() == SpellEffectType.HEALING && targetName.isEmpty()) {
            target = character;
        } else if (!targetName.isEmpty()) {
            Optional<AbstractCharacter> found = character.getCurrentRoom().findAttackableByName(targetName, character);
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

        SpellCasting.CastOutcome outcome = character.castSpell(spell, target);

        connection.send(new CastResult(spell.name(), target.getName(), outcome.selfHeal(), outcome.amount(),
                outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        if (target != character) {
            target.send(new CastReceived(character.getName(), spell.name(), outcome.amount(),
                    outcome.targetHealthAfter(), outcome.targetMaxHealth(), outcome.targetDefeated()));
        }
        if (outcome.targetDefeated()) {
            character.getCombat().setTarget(null);
        }
    }

    private Optional<Spell> resolveKnownSpell(CharacterInstance character, String argument) {
        String lower = argument.toLowerCase();
        return character.getSpellCasting().knownSpellIds().stream().map(spellCatalog::getById)
                .filter(spell -> lower.startsWith(spell.name().toLowerCase()))
                .max(Comparator.comparingInt(spell -> spell.name().length()));
    }
}

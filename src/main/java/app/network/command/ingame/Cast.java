package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.SpellCatalog;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotEnoughMana;
import app.network.message.ingame.SpellNotKnown;
import app.network.message.ingame.SpellOnCooldown;
import app.network.message.ingame.SpellOutOfRange;
import app.network.message.ingame.TargetNotFound;

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
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public boolean requiresNotCasting() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        String trimmed = argument.trim();

        if (trimmed.isEmpty()) {
            connection.send(new Usage("cast <spellUuid>"));
            return;
        }

        String spellToken = trimmed.split("\\s+", 2)[0];

        Optional<UUID> spellId = CommandArguments.tryParseUuid(spellToken);
        if (spellId.isEmpty()) {
            connection.send(new SpellNotKnown(spellToken));
            return;
        }

        Spell spell;
        try {
            spell = spellCatalog.getById(spellId.get());
        } catch (IllegalStateException e) {
            connection.send(new SpellNotKnown(spellToken));
            return;
        }

        switch (character.castSpell(spell, character.getCombat().getTarget())) {
            case AbstractCharacter.CastRequestOutcome.Started ignored -> {
            }
            case AbstractCharacter.CastRequestOutcome.SpellUnknown(var spellName) ->
                connection.send(new SpellNotKnown(spellName));
            case AbstractCharacter.CastRequestOutcome.NoTarget ignored -> connection.send(new NoTargetSelected());
            case AbstractCharacter.CastRequestOutcome.TargetInvalid(var targetId) ->
                connection.send(new TargetNotFound(targetId.toString()));
            case AbstractCharacter.CastRequestOutcome.OutOfRange(var spellName, var targetName) ->
                connection.send(new SpellOutOfRange(spellName, targetName));
            case AbstractCharacter.CastRequestOutcome.OnCooldown(var spellName, var remainingMs) ->
                connection.send(new SpellOnCooldown(spellName, remainingMs));
            case AbstractCharacter.CastRequestOutcome.InsufficientMana(var spellName, var required, var current) ->
                connection.send(new NotEnoughMana(spellName, required, current));
        }
    }
}

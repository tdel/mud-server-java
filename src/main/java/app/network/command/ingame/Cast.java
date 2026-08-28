package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.SpellCatalog;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.AlreadyCasting;
import app.network.message.ingame.SpellNotKnown;

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
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        if (character.isCasting()) {
            connection.send(new AlreadyCasting());
            return;
        }

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

        character.castSpell(spell, character.getCombat().getTarget());
    }
}

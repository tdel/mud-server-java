package app.network.command.ingame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.Spell;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.KnownSpells;

@Component
public class Spells implements CommandHandler {

    @Override
    public String name() {
        return "spells";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        // Un sort octroyé par un objet équipé peut aussi être réellement appris : dans
        // ce cas on ne garde que l'entrée "appris" (granted=false), qui reste valable
        // même après un déséquipement, plutôt que d'afficher le sort deux fois.
        Map<String, KnownSpells.Entry> entriesByName = new LinkedHashMap<>();
        character.getGrantedSpells().stream().map(spell -> toEntry(spell, true))
                .forEach(entry -> entriesByName.put(entry.name(), entry));
        character.getSpellCasting().knownSpells().stream().map(spell -> toEntry(spell, false))
                .forEach(entry -> entriesByName.put(entry.name(), entry));

        List<KnownSpells.Entry> entries = List.copyOf(entriesByName.values());

        connection.send(new KnownSpells(entries));
    }

    private KnownSpells.Entry toEntry(Spell spell, boolean granted) {
        return new KnownSpells.Entry(spell.id(), spell.name(), spell.tier(), spell.description(), spell.manaCost(),
                spell.cooldownSeconds(), spell.range(), spell.effect(), spell.durationSeconds(), granted);
    }
}

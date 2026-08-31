package app.network.command.ingame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.KnownSkills;

@Component
public class Skills implements CommandHandler {

    @Override
    public String name() {
        return "skills";
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
        Map<String, KnownSkills.Entry> entriesByName = new LinkedHashMap<>();
        character.getSkillSystem().getGrantedSkills().stream().map(skill -> toEntry(skill, true))
                .forEach(entry -> entriesByName.put(entry.name(), entry));
        character.getSkillSystem().knownSkills().stream().map(skill -> toEntry(skill, false))
                .forEach(entry -> entriesByName.put(entry.name(), entry));

        List<KnownSkills.Entry> entries = List.copyOf(entriesByName.values());

        connection.send(new KnownSkills(entries));
    }

    private KnownSkills.Entry toEntry(ActiveSkill skill, boolean granted) {
        return new KnownSkills.Entry(skill.id(), skill.name(), skill.tier(), skill.description(), skill.manaCost(),
                skill.cooldownSeconds(), skill.range(), skill.effect(), skill.durationSeconds(), granted);
    }
}

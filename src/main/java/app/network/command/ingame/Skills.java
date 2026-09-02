package app.network.command.ingame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.system.SkillSystem;
import app.game.catalog.SkillCatalog;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.KnownSkills;

@Component
public class Skills implements CommandHandler {

    private final SkillCatalog skillCatalog;

    public Skills(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
    }

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
        SkillSystem skillSystem = character.getSkillSystem();

        // Un sort octroyé par un objet équipé peut aussi être réellement appris : dans
        // ce cas on ne garde que l'entrée "appris" (granted=false), qui reste valable
        // même après un déséquipement, plutôt que d'afficher le sort deux fois.
        Map<String, KnownSkills.Entry> entriesByName = new LinkedHashMap<>();
        skillSystem.getGrantedSkills().stream().map(skill -> toEntry(skill, 1, true))
                .forEach(entry -> entriesByName.put(entry.name(), entry));
        skillSystem.knownSkillLevels().forEach((skillId, level) -> {
            ActiveSkill skill = skillCatalog.getById(skillId);
            entriesByName.put(skill.name(), toEntry(skill, level, false));
        });

        List<KnownSkills.Entry> entries = List.copyOf(entriesByName.values());

        connection.send(new KnownSkills(entries));
    }

    private KnownSkills.Entry toEntry(ActiveSkill skill, int level, boolean granted) {
        return new KnownSkills.Entry(skill.id(), skill.name(), level, skill.manaCostAt(level),
                skill.reuseTimeMs() / 1000, skill.range(), skill.skillType(),
                skill.effects().isEmpty() ? 0 : skill.effects().get(0).time(), granted);
    }
}

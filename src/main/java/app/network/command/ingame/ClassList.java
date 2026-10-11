package app.network.command.ingame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.AbstractNpc;
import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;
import app.domain.actor.system.ClassSystem;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.ClassChangeOptions;
import app.network.message.ingame.ClassOptionView;
import app.network.message.ingame.TargetNotFound;

// "class-list <npcUuid>" : professions proposées par le Grand Master (PNJ
// SKILL_LEARNER, option CLASS_CHANGE de son dialogue), comme le changement de
// classe de L2. Le choix lui-même passe par choose-subclass.
@Component
public class ClassList implements CommandHandler {

    @Override
    public String name() {
        return "class-list";
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
        String raw = argument.trim();
        if (raw.isEmpty()) {
            connection.send(new Usage("class-list <npcUuid>"));
            return;
        }
        Optional<AbstractNpc> master = SkillList.findSkillLearner(connection.character(), raw);
        if (master.isEmpty()) {
            connection.send(new TargetNotFound(raw));
            return;
        }
        connection.send(classChangeOptions(master.get(), connection.character()));
    }

    static ClassChangeOptions classChangeOptions(AbstractNpc master, PlayerInstance character) {
        ClassSystem classSystem = character.getClassSystem();
        List<String> classPath = new ArrayList<>();
        classPath.add(classSystem.getCharacterClass().label());
        classSystem.getSubclasses().stream().map(Subclass::label).forEach(classPath::add);
        List<Subclass> options = classSystem.nextOptions();
        return new ClassChangeOptions(master.getId(), master.getName(), character.getLevelingSystem().getLevel(),
                classSystem.currentClassLabel(), classPath,
                options.isEmpty() ? 0 : classSystem.getSubclasses().size() + 1, classSystem.nextChangeLevel(),
                ClassOptionView.listOf(options));
    }
}

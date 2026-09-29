package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.PlayerInstance;
import app.game.SkillTrainer;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.SkillNotLearnable;
import app.network.message.ingame.TargetNotFound;

// "learn-skill <npcUuid> <skillUuid>" : apprend le level suivant de la
// compétence auprès d'un PNJ SKILL_LEARNER. Succès : SkillLearned (envoyé par
// SkillPersistenceListener) puis la liste LearnableSkills à jour ; refus :
// SkillNotLearnable.
@Component
public class LearnSkill implements CommandHandler {

    private final SkillTrainer skillTrainer;

    public LearnSkill(SkillTrainer skillTrainer) {
        this.skillTrainer = skillTrainer;
    }

    @Override
    public String name() {
        return "learn-skill";
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
        PlayerInstance character = connection.character();
        String[] tokens = argument.trim().split("\\s+");
        if (tokens.length < 2) {
            connection.send(new Usage("learn-skill <npcUuid> <skillUuid>"));
            return;
        }
        Optional<AbstractNpc> learner = SkillList.findSkillLearner(character, tokens[0]);
        if (learner.isEmpty()) {
            connection.send(new TargetNotFound(tokens[0]));
            return;
        }
        Optional<UUID> skillId = CommandArguments.tryParseUuid(tokens[1]);
        if (skillId.isEmpty()) {
            connection.send(new SkillNotLearnable(null, tokens[1], "NOT_IN_TREE", 0));
            return;
        }

        switch (skillTrainer.learn(character, skillId.get())) {
            case SkillTrainer.LearnOutcome.Learned ignored ->
                connection.send(SkillList.learnableSkills(skillTrainer, learner.get(), character));
            case SkillTrainer.LearnOutcome.Refused(var skillName, var reason, var requiredLevel) ->
                connection.send(new SkillNotLearnable(skillId.get(), skillName, reason, requiredLevel));
        }
    }
}

package app.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.actor.AbstractNpc;
import app.domain.actor.NpcType;
import app.domain.actor.instance.PlayerInstance;
import app.game.SkillTrainer;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.LearnableSkills;
import app.network.message.ingame.TargetNotFound;

// Liste des compétences apprenables auprès d'un PNJ SKILL_LEARNER (clic droit
// -> "Apprendre des compétences" ou option SKILL_LEARN de son dialogue côté
// client). Comme `shop`, n'immobilise pas la connexion : la fenêtre
// d'apprentissage du client reste ouverte pendant les autres commandes.
@Component
public class SkillList implements CommandHandler {

    private final SkillTrainer skillTrainer;

    public SkillList(SkillTrainer skillTrainer) {
        this.skillTrainer = skillTrainer;
    }

    @Override
    public String name() {
        return "skill-list";
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
            connection.send(new Usage("skill-list <npcUuid>"));
            return;
        }
        Optional<AbstractNpc> learner = findSkillLearner(connection.character(), raw);
        if (learner.isEmpty()) {
            connection.send(new TargetNotFound(raw));
            return;
        }
        connection.send(learnableSkills(skillTrainer, learner.get(), connection.character()));
    }

    static Optional<AbstractNpc> findSkillLearner(PlayerInstance character, String npcToken) {
        return CommandArguments.tryParseUuid(npcToken)
                .flatMap(id -> character.getMotionSystem().getCurrentMap().findNpcById(id))
                .filter(npc -> npc.getNpcType() == NpcType.SKILL_LEARNER);
    }

    static LearnableSkills learnableSkills(SkillTrainer skillTrainer, AbstractNpc learner, PlayerInstance character) {
        List<SkillTrainer.Offer> offers = skillTrainer.offers(character);
        OptionalInt nextLevel = skillTrainer.nextLevel(offers);
        return new LearnableSkills(learner.getId(), learner.getName(), character.getLevelingSystem().getLevel(),
                nextLevel.isPresent() ? nextLevel.getAsInt() : null,
                offers.stream().filter(SkillTrainer.Offer::available).map(LearnableSkills.Entry::of).toList());
    }
}

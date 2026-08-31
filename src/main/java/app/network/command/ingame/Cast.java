package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import app.domain.ActiveSkill;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.game.catalog.SkillCatalog;
import app.network.CommandArguments;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.NoTargetSelected;
import app.network.message.ingame.NotEnoughMana;
import app.network.message.ingame.SkillNotKnown;
import app.network.message.ingame.SkillOnCooldown;
import app.network.message.ingame.SkillOutOfRange;
import app.network.message.ingame.TargetNotFound;

@Component
public class Cast implements CommandHandler {

    private final SkillCatalog skillCatalog;

    public Cast(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
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
            connection.send(new Usage("cast <skillUuid>"));
            return;
        }

        String skillToken = trimmed.split("\\s+", 2)[0];

        Optional<UUID> skillId = CommandArguments.tryParseUuid(skillToken);
        if (skillId.isEmpty()) {
            connection.send(new SkillNotKnown(skillToken));
            return;
        }

        ActiveSkill activeSkill;
        try {
            activeSkill = skillCatalog.getById(skillId.get());
        } catch (IllegalStateException e) {
            connection.send(new SkillNotKnown(skillToken));
            return;
        }

        switch (character.castSkill(activeSkill, character.getCombat().getTarget())) {
            case AbstractCharacter.CastRequestOutcome.Started ignored -> {
            }
            case AbstractCharacter.CastRequestOutcome.SkillUnknown(var skillName) ->
                connection.send(new SkillNotKnown(skillName));
            case AbstractCharacter.CastRequestOutcome.NoTarget ignored -> connection.send(new NoTargetSelected());
            case AbstractCharacter.CastRequestOutcome.TargetInvalid(var targetId) ->
                connection.send(new TargetNotFound(targetId.toString()));
            case AbstractCharacter.CastRequestOutcome.OutOfRange(var skillName, var targetName) ->
                connection.send(new SkillOutOfRange(skillName, targetName));
            case AbstractCharacter.CastRequestOutcome.OnCooldown(var skillName, var remainingMs) ->
                connection.send(new SkillOnCooldown(skillName, remainingMs));
            case AbstractCharacter.CastRequestOutcome.InsufficientMana(var skillName, var required, var current) ->
                connection.send(new NotEnoughMana(skillName, required, current));
        }
    }
}

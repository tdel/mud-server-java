package app.network.command.ingame;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.domain.ActiveEffect;
import app.domain.actor.instance.CharacterInstance;
import app.network.CommandHandler;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.ActiveEffectsListed;
import app.network.message.ingame.ActiveEffectsListed.EffectView;

@Component
public class Effects implements CommandHandler {

    @Override
    public String name() {
        return "effects";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        Instant now = Instant.now();

        List<EffectView> views = character.getEffectsSystem().active().stream().map(effect -> toView(effect, now))
                .toList();

        connection.send(new ActiveEffectsListed(views));
    }

    private EffectView toView(ActiveEffect effect, Instant now) {
        long secondsRemaining = Duration.between(now, effect.expiresAt()).toSeconds();
        return new EffectView(effect.skillName(), effect.modifiers(), Math.max(0, secondsRemaining));
    }
}

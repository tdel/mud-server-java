package app.network;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CommandRegistry {

    private final List<CommandHandler> actions;
    private Map<ConnectionState, Map<String, CommandHandler>> actionsByStateAndName;

    public CommandRegistry(List<CommandHandler> actions) {
        this.actions = actions;
    }

    @PostConstruct
    void buildIndex() {
        Map<ConnectionState, Map<String, CommandHandler>> index = new EnumMap<>(ConnectionState.class);
        for (CommandHandler action : actions) {
            for (ConnectionState state : action.states()) {
                Map<String, CommandHandler> byName = index.computeIfAbsent(state, s -> new java.util.HashMap<>());
                CommandHandler previous = byName.putIfAbsent(action.name(), action);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Deux actions déclarent le nom '" + action.name() + "' pour l'état " + state);
                }
            }
        }
        this.actionsByStateAndName = index;
    }

    public Optional<CommandHandler> find(ConnectionState state, String name) {
        return Optional.ofNullable(actionsByStateAndName.getOrDefault(state, Map.of()).get(name));
    }
}

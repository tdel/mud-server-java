package fr.idev.mudserver.controller;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import fr.idev.mudserver.network.ConnectionState;

/**
 * {@code List<ControllerHandler>} est injecté nativement par Spring avec tous
 * les beans implémentant l'interface — équivalent direct du
 * {@code #[AutowireIterator('app.action')]} PHP, sans tag YAML à maintenir en
 * synchronisation. La table est construite au démarrage
 * ({@code @PostConstruct}, donc à l'initialisation du contexte) plutôt que
 * paresseusement au premier appel comme côté PHP : une collision de nom entre
 * deux actions du même état échoue ainsi dès le démarrage de l'application, pas
 * au premier joueur qui tape la commande.
 */
@Component
public class ControllerRegistry {

    private final List<ControllerHandler> actions;
    private Map<ConnectionState, Map<String, ControllerHandler>> actionsByStateAndName;

    public ControllerRegistry(List<ControllerHandler> actions) {
        this.actions = actions;
    }

    @PostConstruct
    void buildIndex() {
        Map<ConnectionState, Map<String, ControllerHandler>> index = new EnumMap<>(ConnectionState.class);
        for (ControllerHandler action : actions) {
            for (ConnectionState state : action.states()) {
                Map<String, ControllerHandler> byName = index.computeIfAbsent(state, s -> new java.util.HashMap<>());
                ControllerHandler previous = byName.putIfAbsent(action.name(), action);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Deux actions déclarent le nom '" + action.name() + "' pour l'état " + state);
                }
            }
        }
        this.actionsByStateAndName = index;
    }

    public Optional<ControllerHandler> find(ConnectionState state, String name) {
        return Optional.ofNullable(actionsByStateAndName.getOrDefault(state, Map.of()).get(name));
    }
}

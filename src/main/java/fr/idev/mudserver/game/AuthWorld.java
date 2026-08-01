package fr.idev.mudserver.game;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;

/**
 * Suit tous les clients connectés mais pas encore en train de jouer (les états
 * "connected" et "authed" comptent tous les deux).
 * {@code ConcurrentHashMap.newKeySet()} remplace le {@code SplObjectStorage}
 * PHP — son itérateur tolère un ajout/retrait concurrent sans copie défensive
 * préalable, contrairement à l'original.
 */
@Component
public class AuthWorld {

    private final Set<Session> connectedSessions = ConcurrentHashMap.newKeySet();

    public void enterWorld(Session session) {
        connectedSessions.add(session);
        session.setState(ConnectionState.AUTHED);
    }

    public void exitWorld(Session session) {
        connectedSessions.remove(session);
        session.setState(ConnectionState.CONNECTED);
    }

    /**
     * Détache la session du suivi "connecté mais pas en jeu" sans repasser par
     * l'état {@code CONNECTED} — l'appelant (voir {@code CharacterSelect}) enchaîne
     * directement sur l'état {@code INGAME}. Ne pas remplacer par
     * {@code exitWorld()} + {@code setState()} : {@code exitWorld()} vide aussi le
     * compte attaché à la session.
     */
    public void moveToGameWorld(Session session) {
        connectedSessions.remove(session);
    }

    public boolean isAlreadyConnected(UUID accountId) {
        for (Session session : connectedSessions) {
            if (session.account().id().equals(accountId)) {
                return true;
            }
        }
        return false;
    }
}

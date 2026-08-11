package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.domain.actor.GamePlayer;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();

    void setCharacter(GamePlayer character);

    /**
     * Personnage porté par cette connexion tant qu'elle est en état {@code INGAME}
     * — remplace l'ancien registre centralisé
     * {@code GameWorld.character(Connection)}, qui obligeait chaque appelant à
     * repasser par un bean Spring pour une donnée qui n'a jamais eu besoin d'être
     * partagée entre connexions. Lève {@link IllegalStateException} hors
     * {@code INGAME} plutôt que de renvoyer {@code null} : les handlers
     * {@code controller.ingame.*} sont garantis de ne tourner qu'à cet état par
     * {@code ControllerRegistry}/{@code ControllerDispatcher}, donc un appel ici
     * hors invariant signale un bug d'appelant, pas un cas normal à absorber.
     */
    GamePlayer character();
}

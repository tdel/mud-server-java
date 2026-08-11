package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
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
     * {@code GameWorld.character(Connection)} (voir désormais
     * {@code WorldInstanceService.enterGame}/{@code exitGame}), qui obligeait
     * chaque appelant à repasser par un bean Spring pour une donnée qui n'a jamais
     * eu besoin d'être partagée entre connexions. Lève
     * {@link IllegalStateException} hors {@code INGAME} plutôt que de renvoyer
     * {@code null} : les handlers {@code controller.ingame.*} sont garantis de ne
     * tourner qu'à cet état par
     * {@code ControllerRegistry}/{@code ControllerDispatcher}, donc un appel ici
     * hors invariant signale un bug d'appelant, pas un cas normal à absorber.
     */
    GamePlayer character();

    void setAccount(Account account);

    /**
     * Compte authentifié porté par cette connexion dès l'entrée en {@code LOBBY} et
     * jusqu'à {@code AuthWorld.exitWorld} — remplace l'ancien registre centralisé
     * {@code AuthWorld.account(Connection)}. Lève {@link IllegalStateException} en
     * état {@code CONNECTED} plutôt que de renvoyer {@code null}, même raisonnement
     * que {@link #character()}.
     */
    Account account();

    void setWorldInstance(WorldInstance worldInstance);

    /**
     * {@code WorldInstance} choisie par cette connexion dès l'entrée en
     * {@code CHARSELECT} (voir {@code WorldInstanceService.enterCharSelect}) et
     * jusqu'à {@code exitCharSelect} — reste valide durant {@code INGAME} (voir
     * {@code WorldInstanceService.enterGame}, qui ne la retouche pas). Remplace
     * l'ancien registre centralisé {@code WorldInstanceService.worldInstanceOf}.
     * Lève {@link IllegalStateException} en {@code CONNECTED} ou {@code LOBBY},
     * même raisonnement que {@link #character()}.
     */
    WorldInstance worldInstance();
}

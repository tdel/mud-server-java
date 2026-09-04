package app.network.command.ingame;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.domain.actor.instance.NpcSellerInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.ShopCatalog;
import app.network.message.ingame.TargetNotFound;

// Contrepartie stateless de Talk (qui garde son propre chemin "SHOP" via
// requestBlocking, inchangé, pour le client texte) : envoie directement le
// catalogue sans figer la connexion dans une boucle de prompt bloquante, pour
// coexister avec les commandes normales (déplacement, attaque, ...) pendant
// qu'une fenêtre d'achat GUI reste ouverte côté client 3D.
@Component
public class Shop implements CommandHandler {

    @Override
    public String name() {
        return "shop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("shop <uuid>"));
            return;
        }

        Optional<NpcSellerInstance> seller = CommandArguments.tryParseUuid(raw)
                .flatMap(id -> character.getMotionSystem().getCurrentMap().findNpcById(id))
                .filter(NpcSellerInstance.class::isInstance).map(NpcSellerInstance.class::cast);
        if (seller.isEmpty()) {
            connection.send(new TargetNotFound(raw));
            return;
        }

        List<ShopCatalog.Entry> entries = seller.get().shop().items().stream()
                .map(entry -> new ShopCatalog.Entry(entry.itemTemplate().getId(), entry.itemTemplate().getName(),
                        entry.itemTemplate().getGrade(), entry.price()))
                .toList();
        connection.send(new ShopCatalog(seller.get().getId(), seller.get().getName(), entries,
                character.getInventorySystem().getGold()));
    }
}

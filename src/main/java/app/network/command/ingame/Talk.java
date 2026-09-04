package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandArguments;
import app.network.CommandHandler;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.DialogueOptions;
import app.network.message.ingame.NpcDescription;
import app.network.message.ingame.TargetNotFound;

@Component
public class Talk implements CommandHandler {

    @Override
    public String name() {
        return "talk";
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
        CharacterInstance character = connection.character();
        String raw = argument.trim();

        if (raw.isEmpty()) {
            connection.send(new Usage("talk <uuid>"));
            return;
        }

        Optional<AbstractNpc> npc = CommandArguments.tryParseUuid(raw)
                .flatMap(id -> character.getMotionSystem().getCurrentMap().findNpcById(id));
        if (npc.isEmpty()) {
            connection.send(new TargetNotFound(raw));
            return;
        }

        Optional<AbstractNpc.NpcDialogue> dialogue = npc.get().getDialogue();
        if (dialogue.isEmpty()) {
            connection.send(new NpcDescription(npc.get()));
            return;
        }

        // Le greeting et les options (avec leur texte de réponse déjà inclus, cf.
        // NpcDialogueOption.response) sont envoyés en un seul message : le client
        // GUI (Godot) affiche l'arbre de dialogue localement et n'a pas besoin
        // d'un aller-retour serveur par choix. Une option SHOP se résout côté
        // client via la commande `shop <npcUuid>` (voir Shop.java).
        connection.send(new DialogueOptions(npc.get().getId(), npc.get().getName(), dialogue.get().greeting(),
                dialogue.get().options()));
    }
}

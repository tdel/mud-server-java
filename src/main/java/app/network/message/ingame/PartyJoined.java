package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Réponse à "party-accept". {@code members} liste les membres déjà présents dans le
 * groupe au moment où on le rejoint (leader compris, nous exclus), avec leurs vitaux
 * courants : sans ça, un joueur qui rejoint un groupe de 3+ n'apprend que le leader
 * (leaderId/leaderName) et ignore l'identité des autres membres tant qu'aucun de leurs
 * vitaux ne change (PartyMemberVitalsUpdated n'est diffusé que sur un changement futur,
 * jamais rejoué à l'entrée). Voir aussi PartyMemberJoined, symétrique côté membres déjà
 * présents.
 */
public record PartyJoined(UUID leaderId, String leaderName, int memberCount,
        List<MemberView> members) implements OutputJsonMessage {

    public record MemberView(UUID id, String name, int currentHealth, int maxHealth, int currentMana, int maxMana) {
    }
}

package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.actor.CharacterClass;
import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;
import app.network.OutputJsonMessage;

/**
 * Réponse à "party-accept". {@code members} liste les membres déjà présents
 * dans le groupe au moment où on le rejoint (leader compris, nous exclus), avec
 * leurs vitaux courants : sans ça, un joueur qui rejoint un groupe de 3+
 * n'apprend que le leader (leaderId/leaderName) et ignore l'identité des autres
 * membres tant qu'aucun de leurs vitaux ne change (PartyMemberVitalsUpdated
 * n'est diffusé que sur un changement futur, jamais rejoué à l'entrée). Voir
 * aussi PartyMemberJoined, symétrique côté membres déjà présents. Même raison
 * pour {@code effects} : PartyMemberEffectApplied ne rejoue pas les
 * buffs/debuffs posés avant notre arrivée.
 * {@code level}/{@code characterClass}/ {@code subclass} (null tant qu'aucune
 * n'est choisie) : identité affichée dans la fenêtre de groupe, ensuite tenue à
 * jour par PartyMemberProfileUpdated.
 */
public record PartyJoined(UUID leaderId, String leaderName, int memberCount,
        List<MemberView> members) implements OutputJsonMessage {

    public record MemberView(UUID id, String name, int level, CharacterClass characterClass, Subclass subclass,
            int currentHealth, int maxHealth, int currentMana, int maxMana, List<PartyEffectView> effects) {

        public static MemberView of(PlayerInstance member) {
            return new MemberView(member.getId(), member.getName(), member.getLevelingSystem().getLevel(),
                    member.getClassSystem().getCharacterClass(), member.getClassSystem().getCurrentSubclass(),
                    member.getResourceSystem().getCurrentHealth(), member.getResourceSystem().getMaxHealth(),
                    member.getResourceSystem().getCurrentMana(), member.getResourceSystem().getMaxMana(),
                    PartyEffectView.listOf(member));
        }
    }
}

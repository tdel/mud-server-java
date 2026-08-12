package fr.idev.mudserver.controller.lobby;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.controller.charselect.CharSelectStatus;
import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Party;
import fr.idev.mudserver.domain.PartyMember;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.WorldTemplateSummary;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.PartyService;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.game.WorldTemplateService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.lobby.MemberOffline;
import fr.idev.mudserver.network.message.lobby.NoWorldNamed;
import fr.idev.mudserver.network.message.lobby.NotEnoughPlayers;
import fr.idev.mudserver.network.message.lobby.NotPartyLeader;
import fr.idev.mudserver.network.message.lobby.TooManyPlayers;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.WorldInstanceDao;

@Component
public class WorldEnter implements ControllerHandler {

    private final WorldTemplateService worldTemplateService;
    private final WorldInstanceDao worldInstanceDao;
    private final WorldInstanceService worldInstanceService;
    private final AuthWorld authWorld;
    private final CharSelectStatus charSelectStatus;
    private final PartyService partyService;
    private final AccountDao accountDao;

    public WorldEnter(WorldTemplateService worldTemplateService, WorldInstanceDao worldInstanceDao,
            WorldInstanceService worldInstanceService, AuthWorld authWorld, CharSelectStatus charSelectStatus,
            PartyService partyService, AccountDao accountDao) {
        this.worldTemplateService = worldTemplateService;
        this.worldInstanceDao = worldInstanceDao;
        this.worldInstanceService = worldInstanceService;
        this.authWorld = authWorld;
        this.charSelectStatus = charSelectStatus;
        this.partyService = partyService;
        this.accountDao = accountDao;
    }

    @Override
    public String name() {
        return "world-enter";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.LOBBY);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        String shortName = argument.trim();

        if (shortName.isEmpty()) {
            connection.send(new Usage("world-enter <short-name>"));
            return;
        }

        Optional<WorldTemplateSummary> templateOpt = worldTemplateService.findSummaryByShortName(shortName);
        if (templateOpt.isEmpty()) {
            connection.send(new NoWorldNamed(shortName));
            return;
        }
        WorldTemplateSummary template = templateOpt.get();

        Account account = connection.account();
        Optional<Party> partyOpt = partyService.partyOf(account.getId());

        if (partyOpt.isEmpty()) {
            enterSolo(connection, account, template);
            return;
        }

        enterAsParty(connection, account, partyOpt.get(), template);
    }

    private void enterSolo(Connection connection, Account account, WorldTemplateSummary template) {
        WorldInstance instance = worldInstanceDao.findByAccountIdAndWorldTemplateId(account.getId(), template.id())
                .map(existing -> worldInstanceService.getOrMaterialize(existing.getId()))
                .orElseGet(() -> worldInstanceService.createInstance(template.id(), Set.of(account.getId()),
                        account.getId()));

        worldInstanceService.enterCharSelect(connection, instance);
        charSelectStatus.show(connection, account, instance);
    }

    private void enterAsParty(Connection connection, Account account, Party party, WorldTemplateSummary template) {
        if (!party.isLeader(account.getId())) {
            connection.send(new NotPartyLeader());
            return;
        }

        List<PartyMember> members = party.getMembers();
        if (members.size() < template.minPlayers()) {
            connection.send(new NotEnoughPlayers(template.minPlayers(), members.size()));
            return;
        }
        if (members.size() > template.maxPlayers()) {
            connection.send(new TooManyPlayers(template.maxPlayers(), members.size()));
            return;
        }

        List<Connection> memberConnections = new ArrayList<>();
        Set<UUID> memberAccountIds = new LinkedHashSet<>();
        for (PartyMember member : members) {
            Optional<Connection> memberConnection = authWorld.findConnectionByAccountId(member.accountId());
            if (memberConnection.isEmpty() || memberConnection.get().state() != ConnectionState.LOBBY) {
                String login = accountDao.findById(member.accountId()).map(Account::getLogin).orElse("?");
                connection.send(new MemberOffline(login));
                return;
            }
            memberConnections.add(memberConnection.get());
            memberAccountIds.add(member.accountId());
        }

        WorldInstance instance = worldInstanceService.createInstance(template.id(), memberAccountIds, account.getId());

        for (Connection memberConnection : memberConnections) {
            Account memberAccount = memberConnection.account();
            worldInstanceService.enterCharSelect(memberConnection, instance);
            charSelectStatus.show(memberConnection, memberAccount, instance);
        }

        partyService.dissolve(party);
    }
}

package fr.idev.mudserver.domain.actor.system;

import fr.idev.mudserver.domain.actor.AbstractObject;
import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.NetworkComponent;
import fr.idev.mudserver.network.OutputMessage;

@Service
public class NetworkSystem {

    public void send(AbstractObject entity, OutputMessage message) {
        entity.findComponent(NetworkComponent.class)
                .ifPresent(networkComponent -> networkComponent.connection().send(message));
    }
}

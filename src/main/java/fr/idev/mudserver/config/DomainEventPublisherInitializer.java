package fr.idev.mudserver.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;

@Component
class DomainEventPublisherInitializer {

    DomainEventPublisherInitializer(ApplicationEventPublisher publisher) {
        DomainEventPublisher.initialize(publisher);
    }
}

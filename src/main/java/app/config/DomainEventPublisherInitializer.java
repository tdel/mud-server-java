package app.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import app.domain.actor.event.DomainEventPublisher;

@Component
class DomainEventPublisherInitializer {

    DomainEventPublisherInitializer(ApplicationEventPublisher publisher) {
        DomainEventPublisher.initialize(publisher);
    }
}

package app.domain.actor.event;

import org.springframework.context.ApplicationEventPublisher;

public final class DomainEventPublisher {

    private static volatile ApplicationEventPublisher publisher;

    private DomainEventPublisher() {
    }

    public static void initialize(ApplicationEventPublisher publisher) {
        DomainEventPublisher.publisher = publisher;
    }

    public static void publish(Object event) {
        publisher.publishEvent(event);
    }
}

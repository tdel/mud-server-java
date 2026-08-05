package fr.idev.mudserver.domain.actor.event;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Point d'accès statique à l'{@link ApplicationEventPublisher} Spring, pour que
 * les objets du domaine ({@code GamePlayer}, {@code Room}, {@code Item}...) —
 * de simples POJO, jamais gérés par Spring — puissent publier un événement
 * depuis une méthode métier sans dépendre d'une injection de constructeur.
 * Initialisé une seule fois au démarrage par
 * {@code config.DomainEventPublisherInitializer}, bien avant qu'une mutation de
 * domaine ne soit possible (celle-ci ne survient qu'en traitant une commande
 * telnet, donc après démarrage complet du contexte Spring).
 */
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

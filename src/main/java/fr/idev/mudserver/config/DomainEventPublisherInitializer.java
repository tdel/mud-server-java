package fr.idev.mudserver.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.event.DomainEventPublisher;

/**
 * Capture l'{@link ApplicationEventPublisher} Spring dans le holder statique
 * {@link DomainEventPublisher} au démarrage du contexte, pour que les objets du
 * domaine (jamais gérés par Spring) puissent publier des événements. Voir
 * {@link DomainEventPublisher} pour le compromis (état global statique).
 */
@Component
class DomainEventPublisherInitializer {

    DomainEventPublisherInitializer(ApplicationEventPublisher publisher) {
        DomainEventPublisher.initialize(publisher);
    }
}

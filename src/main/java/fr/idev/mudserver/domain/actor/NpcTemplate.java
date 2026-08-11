package fr.idev.mudserver.domain.actor;

import java.util.UUID;

import fr.idev.mudserver.domain.HexCoordinate;

/**
 * Contenu statique d'un PNJ, symétrique de {@link MonsterTemplate} : un par
 * (WorldTemplate × npc), chargé une fois au démarrage depuis
 * {@code data/worlds/{monde}/npcs.json} (voir {@code WorldTemplateService}).
 * {@code dialogue}/{@code shop} sont déjà résolus (catalogue validé contre les
 * templates d'items, noms/raretés dénormalisés) — {@code WorldTemplateService}
 * fait ce travail une fois au chargement plutôt que de le refaire à chaque
 * instanciation.
 */
public record NpcTemplate(UUID id, String name, UUID roomTemplateId, HexCoordinate cell, String description,
        GameNpc.NpcDialogue dialogue, GameNpcSeller.NpcShop shop) {
}

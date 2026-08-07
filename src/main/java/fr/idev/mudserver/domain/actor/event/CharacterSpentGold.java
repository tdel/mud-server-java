package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#buyItem} une fois {@code inventory.gold} déjà
 * décrémenté — symétrique de {@link CharacterReceivedGold}, écouté par
 * {@code game.actor.CharacterService} pour la persistance et le message au
 * joueur.
 */
public record CharacterSpentGold(GamePlayer character, int amount) {
}

package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#receiveGold(int)} une fois
 * {@code inventory.gold} déjà incrémenté — la persistance
 * ({@code CharacterDao.update}) et le message au joueur ne peuvent pas vivre
 * sur {@link GamePlayer}, simple POJO sans accès à un DAO, d'où ce listener
 * dans {@code game.actor.CharacterService}.
 */
public record CharacterReceivedGold(GamePlayer character, int amount) {
}

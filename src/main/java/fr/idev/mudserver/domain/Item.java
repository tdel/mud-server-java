package fr.idev.mudserver.domain;

import java.util.UUID;

/**
 * Appartient à exactement une room OU un character, jamais les deux — invariant
 * appliqué par {@code fr.idev.mudserver.game.ItemService}, pas ici (voir le plan
 * de migration : un record n'a pas sa place pour une méthode qui lève une exception
 * tout en retournant une nouvelle instance immuable).
 */
public record Item(
        UUID id,
        UUID templateId,
        UUID roomId,
        UUID characterId,
        EquipmentSlot slot
) {
}

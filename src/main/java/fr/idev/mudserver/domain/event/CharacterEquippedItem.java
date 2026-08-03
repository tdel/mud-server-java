package fr.idev.mudserver.domain.event;

import java.util.List;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link Character#equipItem(Item)}. {@code previousOccupants} porte
 * le ou les items déséquipés du même emplacement (0 ou 1 en pratique, la
 * contrainte {@code uniq_character_slot} garantissant qu'il n'y en a jamais
 * plus — mais rien ne l'impose côté objet {@code Character}) : le listener doit
 * persister les deux à la fois, dans une même transaction, pour que la
 * contrainte différée protège le chevauchement transitoire. Nommé
 * {@code CharacterEquippedItem} plutôt que {@code ItemEquipped} pour ne pas
 * entrer en collision avec {@code network.message.ingame.ItemEquipped}, le
 * message envoyé au joueur.
 */
public record CharacterEquippedItem(Character character, Item item, EquipmentSlot slot, List<Item> previousOccupants) {
}

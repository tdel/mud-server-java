package app.domain.actor;

public enum ModifiedStat {
    // HP_REGEN/MP_REGEN : jamais dans les stats de base (0) — seuls des effets et
    // des passifs les modifient, appliqués au montant de régénération par tick
    // (cf. ResourceSystem.healthRegenAmountPerTick / StatSystem.adjust).
    ACCURACY, EVASION, PATK, PDEF, MATK, MDEF, PCRIT, MCRIT, ATKSPD, CASTSPD, SPEED, HP_REGEN, MP_REGEN,
    // Stats des compétences de profession (L2 tier 1/2), sans base (0) : elles ne
    // valent que par les effets et passifs, appliqués via StatSystem.adjust au
    // montant concerné. MAX_HP/MAX_MP : PV/PM max (Boost HP, Battle Roar) ;
    // CRIT_DAMAGE (%) / CRIT_DAMAGE_ADD (dégâts ajoutés) : coups critiques
    // physiques portés ; CRIT_DAMAGE_RECEIVED / CRIT_RATE_RECEIVED (%) : critiques
    // subis ; MAGIC_REUSE (%) : recharge des sorts magiques ; MANA_CHARGE : PM
    // ajoutés à chaque récupération de PM ; ATTACK_RANGE : portée de l'arc ;
    // REFLECT_DAMAGE / TRANSFER_DAMAGE (%) : dégâts de mêlée renvoyés / transmis
    // au serviteur ; RES_* : résistance élémentaire ; CAST_CANCEL : chance d'être
    // interrompu en incantant ; SKILL_EVASION (%) : esquive des compétences
    // physiques ; SHIELD_RATE / SHIELD_DEF : blocage au bouclier ; ATTACK_HOLY :
    // attaque sacrée (Holy Weapon) ; DEBUFF_RESIST (%) : résistance aux sorts
    // d'altération ; RESIST_* (%) : résistance de trait ; DMG_VS_* (%) : dégâts
    // contre une race de monstre ; BOW_RESIST (%) : dégâts d'arc subis.
    MAX_HP, MAX_MP, CRIT_DAMAGE, CRIT_DAMAGE_ADD, CRIT_DAMAGE_RECEIVED, CRIT_RATE_RECEIVED, MAGIC_REUSE, MANA_CHARGE, ATTACK_RANGE, REFLECT_DAMAGE, TRANSFER_DAMAGE, RES_FIRE, RES_WATER, RES_WIND, RES_EARTH, RES_HOLY, RES_DARK, CAST_CANCEL, SKILL_EVASION, SHIELD_RATE, SHIELD_DEF, ATTACK_HOLY, DEBUFF_RESIST, RESIST_STUN, RESIST_SLEEP, RESIST_HOLD, RESIST_DERANGEMENT, RESIST_POISON, RESIST_BLEED, DMG_VS_ANIMAL, DMG_VS_BUG, BOW_RESIST;

    public String label() {
        return switch (this) {
            case ACCURACY -> "accuracy";
            case EVASION -> "evasion";
            case PATK -> "P.Atk.";
            case PDEF -> "P.Def.";
            case MATK -> "M.Atk.";
            case MDEF -> "M.Def.";
            case PCRIT -> "P.Crit.";
            case MCRIT -> "M.Crit.";
            case ATKSPD -> "Atk.Spd.";
            case CASTSPD -> "Casting Spd.";
            case SPEED -> "Speed";
            case HP_REGEN -> "HP Regen.";
            case MP_REGEN -> "MP Regen.";
            case MAX_HP -> "Max HP";
            case MAX_MP -> "Max MP";
            case CRIT_DAMAGE, CRIT_DAMAGE_ADD -> "Critical Damage";
            case CRIT_DAMAGE_RECEIVED -> "Critical Damage Received";
            case CRIT_RATE_RECEIVED -> "Critical Rate Received";
            case MAGIC_REUSE -> "Magic Reuse";
            case MANA_CHARGE -> "MP Recovery";
            case ATTACK_RANGE -> "Attack Range";
            case REFLECT_DAMAGE -> "Reflect Damage";
            case TRANSFER_DAMAGE -> "Transfer Pain";
            case RES_FIRE -> "Fire Res.";
            case RES_WATER -> "Water Res.";
            case RES_WIND -> "Wind Res.";
            case RES_EARTH -> "Earth Res.";
            case RES_HOLY -> "Holy Res.";
            case RES_DARK -> "Dark Res.";
            case CAST_CANCEL -> "Cast Interruption";
            case SKILL_EVASION -> "Skill Evasion";
            case SHIELD_RATE -> "Shield Block Rate";
            case SHIELD_DEF -> "Shield Defense";
            case ATTACK_HOLY -> "Holy Attack";
            case DEBUFF_RESIST -> "Debuff Res.";
            case RESIST_STUN -> "Stun Res.";
            case RESIST_SLEEP -> "Sleep Res.";
            case RESIST_HOLD -> "Hold Res.";
            case RESIST_DERANGEMENT -> "Mental Res.";
            case RESIST_POISON -> "Poison Res.";
            case RESIST_BLEED -> "Bleed Res.";
            case DMG_VS_ANIMAL -> "Damage vs Animals";
            case DMG_VS_BUG -> "Damage vs Insects";
            case BOW_RESIST -> "Bow Res.";
        };
    }
}

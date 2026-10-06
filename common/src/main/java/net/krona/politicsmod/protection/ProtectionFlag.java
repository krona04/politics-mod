package net.krona.politicsmod.protection;

import net.krona.politicsmod.config.PoliticsConfig;

import java.util.Locale;

/**
 * What is allowed inside a country's land. A city can override its country's value,
 * and a country can override the server default from the config.
 */
public enum ProtectionFlag {
    /** Players can hurt each other (players at war can always fight). */
    PVP,
    /** Explosions not caused by mobs (TNT, beds, respawn anchors) break blocks. */
    EXPLOSIONS,
    /** Explosions caused by mobs (creepers, ghasts, withers) break blocks. */
    MOB_EXPLOSIONS,
    /** Fire spreads and burns blocks. */
    FIRE_SPREAD,
    /** Outsiders can pick up dropped items. */
    ITEM_PICKUP,
    /** Outsiders can enter (allies and countries at war can always enter). */
    ENTRY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ProtectionFlag byKey(String key) {
        for (ProtectionFlag f : values()) if (f.key().equals(key)) return f;
        return null;
    }

    public boolean defaultValue() {
        PoliticsConfig cfg = PoliticsConfig.get();
        return switch (this) {
            case PVP -> cfg.flagDefaultPvp;
            case EXPLOSIONS -> cfg.flagDefaultExplosions;
            case MOB_EXPLOSIONS -> cfg.flagDefaultMobExplosions;
            case FIRE_SPREAD -> cfg.flagDefaultFireSpread;
            case ITEM_PICKUP -> cfg.flagDefaultItemPickup;
            case ENTRY -> cfg.flagDefaultEntry;
        };
    }
}

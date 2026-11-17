package net.krona.politicsmod.realm;

import java.util.Locale;

/** What a modifier changes. Every value but UNREST is a percentage added to 100%. */
public enum ModifierType {
    /** Income of tax blocks, colonies and villages. */
    TAX_INCOME,
    /** Income of active embassies. */
    EMBASSY_INCOME,
    /** Territory upkeep. */
    UPKEEP,
    /** Price of new country chunks. */
    LAND_PRICE,
    /** How long enemies must hold a chunk of the country to capture it. */
    SIEGE_DEFENSE,
    /** Unrest of every city (0.5.1). Unlike the others, in unrest points, not percent. */
    UNREST;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key of the name shown in tooltips and commands. */
    public String nameKey() {
        return "realm.politicsmod.modifier." + key();
    }

    public static ModifierType byKey(String key) {
        for (ModifierType t : values()) if (t.key().equals(key)) return t;
        return null;
    }
}

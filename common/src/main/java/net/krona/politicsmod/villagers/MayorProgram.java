package net.krona.politicsmod.villagers;

import java.util.Locale;

/**
 * What a villager promises when running for mayor, and does once elected. Players vote for the program as much as
 * for the villager.
 */
public enum MayorProgram {
    /** Collects more: the city's tax blocks bring more, the townsfolk grumble. */
    TAXES(15, 5),
    /** Keeps the peace: less unrest, a little less tax. */
    ORDER(-5, -12),
    /** Builds: buys the next development level from the city treasury whenever it can afford it. */
    BUILDER(0, -3);

    /** Extra tax percent of the city's tax blocks. */
    public final int taxPercent;
    /** Unrest added in the city. */
    public final int unrest;

    MayorProgram(int taxPercent, int unrest) {
        this.taxPercent = taxPercent;
        this.unrest = unrest;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "villager.politicsmod.program." + key();
    }

    public static MayorProgram byKey(String key) {
        for (MayorProgram p : values()) if (p.key().equals(key)) return p;
        return null;
    }
}

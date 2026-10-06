package net.krona.politicsmod.politics;

import java.util.Locale;

/** Laws a leader can pass. Each one is either in force or not. */
public enum Law {
    /** Only citizens may enter the country; enemies at war still can. */
    CLOSED_BORDERS,
    /** Lower tax income, but sieges against the country take longer. */
    MARTIAL_LAW,
    /** Higher embassy income and no market fee with other free-trade countries. */
    FREE_TRADE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key of the law's name. */
    public String nameKey() {
        return "law.politicsmod." + key();
    }

    public static Law byKey(String key) {
        for (Law law : values()) if (law.key().equals(key)) return law;
        return null;
    }
}

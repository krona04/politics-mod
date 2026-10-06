package net.krona.politicsmod.politics;

import net.krona.politicsmod.config.PoliticsConfig;

import java.util.List;
import java.util.UUID;

/** Actions a server can grant to mayors and citizens through rolePermissions in the config. */
public enum RoleAction {
    INVITE, KICK, CLAIM_CITY, MANAGE_EMBASSIES, SPEND_TREASURY, BUY_TERRITORY, MANAGE_PLOTS, SELL_FOR_STATE;

    /** The leader can do everything; guests can do nothing; mayors and citizens follow the config. */
    public boolean allowed(CountryRole role) {
        if (role == CountryRole.LEADER) return true;
        if (role == CountryRole.GUEST) return false;
        List<String> granted = PoliticsConfig.get().rolePermissions.get(role.name());
        return granted != null && granted.contains(name());
    }

    public boolean allowed(Country country, UUID player) {
        return country != null && allowed(country.getRole(player));
    }

    /** Bit mask of allowed actions, sent to the client so the GUI hides what the player can't use. */
    public static int mask(CountryRole role) {
        int mask = 0;
        for (RoleAction a : values()) if (a.allowed(role)) mask |= 1 << a.ordinal();
        return mask;
    }

    public boolean in(int mask) {
        return (mask & (1 << ordinal())) != 0;
    }
}

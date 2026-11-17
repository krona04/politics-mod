package net.krona.politicsmod.statecraft;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.krona.politicsmod.realm.Realm;

import java.util.Map;

/**
 * What conditions look at and effects change: a country, and for city events or decisions one of its cities
 * (null for country-wide ones). City numbers are worked out once per scope.
 */
public final class Scope {
    public final PoliticsManager mgr;
    public final Country country;
    public final String city;
    private Map<String, Realm.CityStats> cities;

    public Scope(PoliticsManager mgr, Country country, String city) {
        this.mgr = mgr;
        this.country = country;
        this.city = city == null || city.isEmpty() ? null : city;
    }

    public Scope withCity(String city) {
        Scope s = new Scope(mgr, country, city);
        s.cities = cities;
        return s;
    }

    public long cycle() {
        return mgr.getCycleCount();
    }

    public Map<String, Realm.CityStats> cities() {
        if (cities == null) cities = Realm.cities(mgr, country);
        return cities;
    }

    /** Numbers worked out again after effects changed the country. */
    public void invalidate() {
        cities = null;
    }

    /** The city effects without a city of their own act on: the event's or decision's city, else the capital. */
    public String targetCity() {
        if (city != null && country.cities.contains(city)) return city;
        if (country.capital != null && country.cities.contains(country.capital)) return country.capital;
        return country.cities.isEmpty() ? null : country.cities.get(0);
    }

    public int count(DiplomacyStatus status) {
        int n = 0;
        for (String other : mgr.getCountries().keySet()) {
            if (!other.equals(country.getName()) && mgr.getDiplomacy(country.getName(), other) == status) n++;
        }
        return n;
    }

    public int activeEmbassies() {
        int n = 0;
        for (String partner : country.embassyLinks.values()) {
            if (mgr.getCountry(partner) != null && !mgr.isAtWar(country.getName(), partner)) n++;
        }
        return n;
    }

    public int maxUnrest() {
        int max = 0;
        for (Realm.CityStats s : cities().values()) max = Math.max(max, s.unrestValue());
        return max;
    }

    public int totalDevelopment() {
        int sum = 0;
        for (Realm.CityStats s : cities().values()) sum += s.development();
        return sum;
    }

    public Realm.CityStats cityStats() {
        String c = targetCity();
        return c == null ? null : cities().get(c);
    }

    public int countryAge() {
        return country.foundedCycle < 0 ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, cycle() - country.foundedCycle);
    }
}

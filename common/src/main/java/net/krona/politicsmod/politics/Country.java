package net.krona.politicsmod.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Country {
    private final String name;
    private final Map<UUID, CountryRole> members = new HashMap<>();
    public final List<Long> taxBlocks = new ArrayList<>();

    // Economy and visuals
    public int balance;
    public String capital = "";
    public String flagUrl = "";

    // Cities
    public final List<String>            cities       = new ArrayList<>();
    public final Map<String, Integer>    cityColors   = new HashMap<>();

    // City treasuries and autonomy
    public final Map<String, Integer>    cityBalances = new HashMap<>();
    public final Map<String, UUID>       cityMayors   = new HashMap<>();
    public int federalTaxRate = 15; // % of city income -> federal treasury

    // Salaries paid from the treasury every economy cycle, and the income tax taken from them
    public final Map<UUID, Integer> salaries = new HashMap<>();
    public int incomeTaxRate = net.krona.politicsmod.config.PoliticsConfig.get().defaultIncomeTaxPercent;

    // Protection flag overrides: country-wide and per city (missing = inherit)
    public final Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean> flags =
            new java.util.EnumMap<>(net.krona.politicsmod.protection.ProtectionFlag.class);
    public final Map<String, Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean>> cityFlags = new HashMap<>();

    // Laws in force and when they last changed (ms)
    public final java.util.Set<Law> laws = java.util.EnumSet.noneOf(Law.class);
    public long lawChangedAt = 0L;

    // Elections: the running one, citizens asking for a new leader, and when the last one ended (ms)
    public Election election = null;
    public final java.util.Set<UUID> petition = new java.util.HashSet<>();
    public long lastElectionAt = 0L;

    /** Set by the economy cycle, so going bankrupt and recovering are announced once. */
    public boolean bankrupt = false;

    // Infrastructure blocks
    public final List<Long>           embassyBlocks = new ArrayList<>();
    public final java.util.Map<Long, String> embassyLinks  = new java.util.HashMap<>();
    public final List<Long>           radarBlocks   = new ArrayList<>();
    public final List<Long>           vaultBlocks   = new ArrayList<>();

    public Country(String name, UUID founder) {
        this.name = name;
        this.balance = net.krona.politicsmod.config.PoliticsConfig.get().startingTreasury;
        if (founder != null) this.members.put(founder, CountryRole.LEADER);
    }

    public String getName() { return name; }

    public boolean hasLaw(Law law) { return laws.contains(law); }

    public boolean isBankrupt() { return balance < 0; }

    /** The member who leads the country, or null. */
    public UUID getLeader() {
        for (Map.Entry<UUID, CountryRole> e : members.entrySet()) if (e.getValue() == CountryRole.LEADER) return e.getKey();
        return null;
    }

    /** A stable colour for maps, picked from the name so every country looks different. */
    public static int mapColor(String name) {
        float hue = (name.hashCode() & 0xFFFF) / 65536f;
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.65f, 0.9f) & 0xFFFFFF;
    }

    public void setRole(UUID player, CountryRole role) {
        if (role == CountryRole.GUEST) {
            members.remove(player);
            salaries.remove(player);
            petition.remove(player);
            cityMayors.values().removeIf(player::equals);
        } else {
            members.put(player, role);
        }
    }
    public CountryRole getRole(UUID player) { return members.getOrDefault(player, CountryRole.GUEST); }
    public Map<UUID, CountryRole> getMembers() { return members; }

    // ── NBT SAVE ─────────────────────────────────────────────────────────

    public CompoundTag save(CompoundTag tag) {
        tag.putString("name", this.name);
        tag.putInt("balance", this.balance);
        tag.putString("capital", this.capital != null ? this.capital : "");
        tag.putString("flagUrl", this.flagUrl != null ? this.flagUrl : "");
        tag.putInt("federalTaxRate", this.federalTaxRate);
        tag.putInt("incomeTaxRate", this.incomeTaxRate);

        ListTag salaryList = new ListTag();
        for (Map.Entry<UUID, Integer> e : salaries.entrySet()) {
            CompoundTag st = new CompoundTag();
            st.putUUID("player", e.getKey());
            st.putInt("amount", e.getValue());
            salaryList.add(st);
        }
        tag.put("salaries", salaryList);

        // Members
        ListTag membersList = new ListTag();
        for (Map.Entry<UUID, CountryRole> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("UUID", e.getKey());
            m.putString("Role", e.getValue().name());
            membersList.add(m);
        }
        tag.put("Members", membersList);

        // Cities (name + color)
        ListTag citiesTag = new ListTag();
        for (String city : cities) {
            CompoundTag ct = new CompoundTag();
            ct.putString("n", city);
            if (cityColors.containsKey(city)) ct.putInt("color", cityColors.get(city));
            citiesTag.add(ct);
        }
        tag.put("cities", citiesTag);

        // TaxBlocks
        ListTag taxList = new ListTag();
        for (Long pos : taxBlocks) {
            CompoundTag pt = new CompoundTag();
            pt.putLong("pos", pos);
            taxList.add(pt);
        }
        tag.put("taxBlocks", taxList);

        // City treasuries
        CompoundTag balTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : cityBalances.entrySet()) balTag.putInt(e.getKey(), e.getValue());
        tag.put("cityBalances", balTag);

        // City mayors
        ListTag mayorList = new ListTag();
        for (Map.Entry<String, UUID> e : cityMayors.entrySet()) {
            CompoundTag mt = new CompoundTag();
            mt.putString("city", e.getKey());
            mt.putUUID("mayor", e.getValue());
            mayorList.add(mt);
        }
        tag.put("cityMayors", mayorList);

        // Embassy blocks + links
        ListTag embassyList = new ListTag();
        for (Long pos : embassyBlocks) {
            CompoundTag pt = new CompoundTag();
            pt.putLong("pos", pos);
            String link = embassyLinks.get(pos);
            if (link != null) pt.putString("link", link);
            embassyList.add(pt);
        }
        tag.put("embassyBlocks", embassyList);

        // Radar blocks
        ListTag radarList = new ListTag();
        for (Long pos : radarBlocks) { CompoundTag pt = new CompoundTag(); pt.putLong("pos", pos); radarList.add(pt); }
        tag.put("radarBlocks", radarList);

        // Vault blocks
        ListTag vaultList = new ListTag();
        for (Long pos : vaultBlocks) { CompoundTag pt = new CompoundTag(); pt.putLong("pos", pos); vaultList.add(pt); }
        tag.put("vaultBlocks", vaultList);

        // Laws
        ListTag lawList = new ListTag();
        for (Law law : laws) lawList.add(net.minecraft.nbt.StringTag.valueOf(law.key()));
        tag.put("laws", lawList);
        tag.putLong("lawChangedAt", lawChangedAt);

        // Elections
        if (election != null) tag.put("election", election.save());
        ListTag petitionList = new ListTag();
        for (UUID id : petition) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("id", id);
            petitionList.add(pt);
        }
        tag.put("petition", petitionList);
        tag.putLong("lastElectionAt", lastElectionAt);
        tag.putBoolean("bankrupt", bankrupt);

        // Protection flags
        tag.put("flags", saveFlags(flags));
        CompoundTag cityFlagTag = new CompoundTag();
        for (Map.Entry<String, Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean>> e : cityFlags.entrySet()) {
            if (!e.getValue().isEmpty()) cityFlagTag.put(e.getKey(), saveFlags(e.getValue()));
        }
        tag.put("cityFlags", cityFlagTag);

        return tag;
    }

    // ── NBT LOAD ─────────────────────────────────────────────────────────

    public static Country load(CompoundTag tag) {
        Country country = new Country(tag.getString("name"), null);

        country.balance       = tag.getInt("balance");
        country.capital       = tag.getString("capital");
        country.flagUrl       = tag.getString("flagUrl");
        country.federalTaxRate = tag.contains("federalTaxRate") ? tag.getInt("federalTaxRate") : 15;
        if (tag.contains("incomeTaxRate")) country.incomeTaxRate = tag.getInt("incomeTaxRate");
        ListTag salaryList = tag.getList("salaries", Tag.TAG_COMPOUND);
        for (int i = 0; i < salaryList.size(); i++) {
            CompoundTag st = salaryList.getCompound(i);
            if (st.hasUUID("player")) country.salaries.put(st.getUUID("player"), st.getInt("amount"));
        }

        // Backward compatibility
        if (tag.hasUUID("owner")) country.members.put(tag.getUUID("owner"), CountryRole.LEADER);

        if (tag.contains("Members")) {
            ListTag ml = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < ml.size(); i++) {
                CompoundTag m = ml.getCompound(i);
                country.members.put(m.getUUID("UUID"), CountryRole.valueOf(m.getString("Role")));
            }
        }

        ListTag cl = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag ct = cl.getCompound(i);
            String cityName = ct.getString("n");
            country.cities.add(cityName);
            if (ct.contains("color")) country.cityColors.put(cityName, ct.getInt("color"));
        }

        if (tag.contains("taxBlocks")) {
            ListTag tl = tag.getList("taxBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < tl.size(); i++) country.taxBlocks.add(tl.getCompound(i).getLong("pos"));
        }

        if (tag.contains("cityBalances")) {
            CompoundTag bt = tag.getCompound("cityBalances");
            for (String key : bt.getAllKeys()) country.cityBalances.put(key, bt.getInt(key));
        }

        if (tag.contains("cityMayors")) {
            ListTag ml = tag.getList("cityMayors", Tag.TAG_COMPOUND);
            for (int i = 0; i < ml.size(); i++) {
                CompoundTag mt = ml.getCompound(i);
                country.cityMayors.put(mt.getString("city"), mt.getUUID("mayor"));
            }
        }

        if (tag.contains("embassyBlocks")) {
            ListTag el = tag.getList("embassyBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < el.size(); i++) {
                CompoundTag pt = el.getCompound(i);
                long pos = pt.getLong("pos");
                country.embassyBlocks.add(pos);
                if (pt.contains("link")) country.embassyLinks.put(pos, pt.getString("link"));
            }
        }
        if (tag.contains("radarBlocks")) {
            ListTag rl = tag.getList("radarBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < rl.size(); i++) country.radarBlocks.add(rl.getCompound(i).getLong("pos"));
        }
        if (tag.contains("vaultBlocks")) {
            ListTag vl = tag.getList("vaultBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < vl.size(); i++) country.vaultBlocks.add(vl.getCompound(i).getLong("pos"));
        }

        ListTag lawList = tag.getList("laws", Tag.TAG_STRING);
        for (int i = 0; i < lawList.size(); i++) {
            Law law = Law.byKey(lawList.getString(i));
            if (law != null) country.laws.add(law);
        }
        country.lawChangedAt = tag.getLong("lawChangedAt");
        if (tag.contains("election")) country.election = Election.load(tag.getCompound("election"));
        ListTag petitionList = tag.getList("petition", Tag.TAG_COMPOUND);
        for (int i = 0; i < petitionList.size(); i++) {
            if (petitionList.getCompound(i).hasUUID("id")) country.petition.add(petitionList.getCompound(i).getUUID("id"));
        }
        country.lastElectionAt = tag.getLong("lastElectionAt");
        country.bankrupt = tag.getBoolean("bankrupt");

        loadFlags(tag.getCompound("flags"), country.flags);
        CompoundTag cityFlagTag = tag.getCompound("cityFlags");
        for (String city : cityFlagTag.getAllKeys()) {
            Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean> m =
                    new java.util.EnumMap<>(net.krona.politicsmod.protection.ProtectionFlag.class);
            loadFlags(cityFlagTag.getCompound(city), m);
            country.cityFlags.put(city, m);
        }

        return country;
    }

    private static CompoundTag saveFlags(Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean> flags) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<net.krona.politicsmod.protection.ProtectionFlag, Boolean> e : flags.entrySet()) {
            t.putBoolean(e.getKey().key(), e.getValue());
        }
        return t;
    }

    private static void loadFlags(CompoundTag t, Map<net.krona.politicsmod.protection.ProtectionFlag, Boolean> into) {
        for (String key : t.getAllKeys()) {
            net.krona.politicsmod.protection.ProtectionFlag flag = net.krona.politicsmod.protection.ProtectionFlag.byKey(key);
            if (flag != null) into.put(flag, t.getBoolean(key));
        }
    }
}

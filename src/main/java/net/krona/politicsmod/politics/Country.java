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

    // Экономика и визуал
    public int balance;
    public String capital = "";
    public String flagUrl = "";

    // Страновые города
    public final List<String>            cities       = new ArrayList<>();
    public final Map<String, Integer>    cityColors   = new HashMap<>();

    // Столп 3 — городские казны и автономия
    public final Map<String, Integer>    cityBalances = new HashMap<>();
    public final Map<String, UUID>       cityMayors   = new HashMap<>();
    public int federalTaxRate = 15; // % дохода города -> федеральная казна

    // Столп 4 — Инфраструктурные блоки
    public final List<Long>           embassyBlocks = new ArrayList<>();
    public final java.util.Map<Long, String> embassyLinks  = new java.util.HashMap<>();
    public final List<Long>           radarBlocks   = new ArrayList<>();
    public final List<Long>           vaultBlocks   = new ArrayList<>();

    public Country(String name, UUID founder) {
        this.name = name;
        this.balance = 1000;
        if (founder != null) this.members.put(founder, CountryRole.LEADER);
    }

    public String getName() { return name; }

    public void setRole(UUID player, CountryRole role) {
        if (role == CountryRole.GUEST) members.remove(player);
        else members.put(player, role);
    }
    public CountryRole getRole(UUID player) { return members.getOrDefault(player, CountryRole.GUEST); }
    public Map<UUID, CountryRole> getMembers() { return members; }

    // ── NBT СОХРАНЕНИЕ ───────────────────────────────────────────────────

    public CompoundTag save(CompoundTag tag) {
        tag.putString("name", this.name);
        tag.putInt("balance", this.balance);
        tag.putString("capital", this.capital != null ? this.capital : "");
        tag.putString("flagUrl", this.flagUrl != null ? this.flagUrl : "");
        tag.putInt("federalTaxRate", this.federalTaxRate);

        // Жители
        ListTag membersList = new ListTag();
        for (Map.Entry<UUID, CountryRole> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("UUID", e.getKey());
            m.putString("Role", e.getValue().name());
            membersList.add(m);
        }
        tag.put("Members", membersList);

        // Города (имя + цвет)
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

        // Городские казны
        CompoundTag balTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : cityBalances.entrySet()) balTag.putInt(e.getKey(), e.getValue());
        tag.put("cityBalances", balTag);

        // Городские мэры
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

        return tag;
    }

    // ── NBT ЗАГРУЗКА ─────────────────────────────────────────────────────

    public static Country load(CompoundTag tag) {
        Country country = new Country(tag.getString("name"), null);

        country.balance       = tag.getInt("balance");
        country.capital       = tag.getString("capital");
        country.flagUrl       = tag.getString("flagUrl");
        country.federalTaxRate = tag.contains("federalTaxRate") ? tag.getInt("federalTaxRate") : 15;

        // Обратная совместимость
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

        return country;
    }
}

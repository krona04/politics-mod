package net.krona.politicsmod;

import net.krona.politicsmod.config.PoliticsConfig;

import net.krona.politicsmod.network.SyncChunkPayload;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.krona.politicsmod.politics.MarketListing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.krona.politicsmod.network.ModNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PoliticsManager extends SavedData {

    // ── DATA ─────────────────────────────────────────────────────────────
    private final Map<String, Country>                      countries    = new HashMap<>();
    private final Map<ChunkPos, String>                     chunkOwners  = new HashMap<>();
    private final Map<ChunkPos, String>                     chunkCity    = new HashMap<>();
    private final Map<String, Map<String, DiplomacyStatus>> diplomacy    = new HashMap<>();
    private final Map<String, Long>                         warCooldowns = new HashMap<>();
    private final Map<String, String[]>                     tributes     = new HashMap<>();
    private final List<MarketListing>                       listings     = new ArrayList<>();
    private long                                            nextListingId = 1;

    public PoliticsManager() {}

    // ── INSTANCE ─────────────────────────────────────────────────────────

    public static PoliticsManager get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PoliticsManager::new, PoliticsManager::load, null),
                "politicsmod_data");
        }
        return null;
    }

    // ── NBT SAVE ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag countryList = new ListTag();
        for (Country c : countries.values()) countryList.add(c.save(new CompoundTag()));
        tag.put("countries", countryList);

        ListTag claimsList = new ListTag();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey().toLong());
            t.putString("country", e.getValue());
            String city = chunkCity.get(e.getKey());
            if (city != null && !city.isEmpty()) t.putString("city", city);
            claimsList.add(t);
        }
        tag.put("claims", claimsList);

        ListTag dipList = new ListTag();
        for (Map.Entry<String, Map<String, DiplomacyStatus>> outer : diplomacy.entrySet()) {
            for (Map.Entry<String, DiplomacyStatus> inner : outer.getValue().entrySet()) {
                if (outer.getKey().compareTo(inner.getKey()) < 0) {
                    CompoundTag r = new CompoundTag();
                    r.putString("a", outer.getKey()); r.putString("b", inner.getKey());
                    r.putString("s", inner.getValue().name());
                    dipList.add(r);
                }
            }
        }
        tag.put("diplomacy", dipList);

        ListTag tributeList = new ListTag();
        for (Map.Entry<String, String[]> e : tributes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("payer", e.getKey());
            t.putString("receiver", e.getValue()[0]);
            t.putInt("rate", Integer.parseInt(e.getValue()[1]));
            tributeList.add(t);
        }
        tag.put("tributes", tributeList);

        ListTag listingList = new ListTag();
        for (MarketListing l : listings) listingList.add(l.save(provider));
        tag.put("marketListings", listingList);
        tag.putLong("nextListingId", nextListingId);

        return tag;
    }

    // ── NBT LOAD ─────────────────────────────────────────────────────────

    public static PoliticsManager load(CompoundTag tag, HolderLookup.Provider provider) {
        PoliticsManager m = new PoliticsManager();

        for (Tag t : tag.getList("countries", Tag.TAG_COMPOUND)) {
            Country c = Country.load((CompoundTag) t);
            m.countries.put(c.getName(), c);
        }
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ChunkPos pos = new ChunkPos(e.getLong("pos"));
            m.chunkOwners.put(pos, e.getString("country"));
            if (e.contains("city")) m.chunkCity.put(pos, e.getString("city"));
        }
        if (tag.contains("diplomacy")) {
            for (int i = 0; i < tag.getList("diplomacy", Tag.TAG_COMPOUND).size(); i++) {
                CompoundTag r = tag.getList("diplomacy", Tag.TAG_COMPOUND).getCompound(i);
                String a = r.getString("a"), b = r.getString("b");
                DiplomacyStatus s = DiplomacyStatus.valueOf(r.getString("s"));
                m.diplomacy.computeIfAbsent(a, k -> new HashMap<>()).put(b, s);
                m.diplomacy.computeIfAbsent(b, k -> new HashMap<>()).put(a, s);
            }
        }
        if (tag.contains("tributes")) {
            for (int i = 0; i < tag.getList("tributes", Tag.TAG_COMPOUND).size(); i++) {
                CompoundTag t = tag.getList("tributes", Tag.TAG_COMPOUND).getCompound(i);
                m.tributes.put(t.getString("payer"),
                    new String[]{t.getString("receiver"), String.valueOf(t.getInt("rate"))});
            }
        }
        if (tag.contains("marketListings")) {
            ListTag ll = tag.getList("marketListings", Tag.TAG_COMPOUND);
            for (int i = 0; i < ll.size(); i++) {
                MarketListing.load(ll.getCompound(i), provider).ifPresent(m.listings::add);
            }
        }
        m.nextListingId = Math.max(tag.getLong("nextListingId"), 1);
        for (MarketListing l : m.listings) m.nextListingId = Math.max(m.nextListingId, l.id + 1);
        return m;
    }

    public void saveData() { this.setDirty(); }

    // ── DIPLOMACY ────────────────────────────────────────────────────────

    public DiplomacyStatus getDiplomacy(String a, String b) {
        return diplomacy.getOrDefault(a, Collections.emptyMap()).getOrDefault(b, DiplomacyStatus.NEUTRAL);
    }
    public void setDiplomacy(String a, String b, DiplomacyStatus status) {
        if (status == DiplomacyStatus.NEUTRAL) {
            Map<String, DiplomacyStatus> ma = diplomacy.get(a);
            if (ma != null) { ma.remove(b); if (ma.isEmpty()) diplomacy.remove(a); }
            Map<String, DiplomacyStatus> mb = diplomacy.get(b);
            if (mb != null) { mb.remove(a); if (mb.isEmpty()) diplomacy.remove(b); }
        } else {
            diplomacy.computeIfAbsent(a, k -> new HashMap<>()).put(b, status);
            diplomacy.computeIfAbsent(b, k -> new HashMap<>()).put(a, status);
        }
        setDirty();
    }
    public boolean isAtWar(String a, String b)  { return getDiplomacy(a, b) == DiplomacyStatus.WAR; }
    public boolean isAllied(String a, String b) { return getDiplomacy(a, b) == DiplomacyStatus.ALLIANCE; }

    public boolean isBorderChunk(ChunkPos pos, String owner) {
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            String n = chunkOwners.get(new ChunkPos(pos.x+d[0], pos.z+d[1]));
            if (n == null || !n.equals(owner)) return true;
        }
        return false;
    }
    public boolean canDeclareWar(String c) { Long l = warCooldowns.get(c); return l==null||System.currentTimeMillis()-l>=PoliticsConfig.get().warCooldownMs(); }
    public long getWarCooldownTimestamp(String c) { return warCooldowns.getOrDefault(c, 0L); }
    public void recordWarDeclaration(String c)    { warCooldowns.put(c, System.currentTimeMillis()); }

    // ── MARKET ───────────────────────────────────────────────────────────

    public List<MarketListing> getListings() { return Collections.unmodifiableList(listings); }

    public MarketListing addListing(String seller, UUID listedBy, net.minecraft.world.item.ItemStack stack, int price) {
        MarketListing listing = new MarketListing(nextListingId++, seller, listedBy, stack, price, System.currentTimeMillis());
        listings.add(listing);
        setDirty();
        return listing;
    }

    public MarketListing findListing(long id) {
        for (MarketListing l : listings) if (l.id == id) return l;
        return null;
    }

    public boolean removeListing(long id) {
        boolean removed = listings.removeIf(l -> l.id == id);
        if (removed) setDirty();
        return removed;
    }

    public int countListings(String seller) {
        int n = 0;
        for (MarketListing l : listings) if (l.seller.equals(seller)) n++;
        return n;
    }

    // ── TRIBUTES ─────────────────────────────────────────────────────────

    public void setTribute(String payer, String receiver, int rate) { tributes.put(payer, new String[]{receiver, String.valueOf(rate)}); setDirty(); }
    public void removeTribute(String payer)             { tributes.remove(payer); setDirty(); }
    public String[] getTribute(String payer)            { return tributes.get(payer); }
    public Map<String, String[]> getAllTributes()        { return Collections.unmodifiableMap(tributes); }

    // ── CITY MAYORS ──────────────────────────────────────────────────────

    /** Appoints a city mayor. */
    public void setCityMayor(String countryName, String cityName, UUID mayorUUID) {
        Country c = countries.get(countryName);
        if (c == null) return;
        // A player can be mayor of only one city
        c.cityMayors.entrySet().removeIf(e -> e.getValue().equals(mayorUUID));
        c.cityMayors.put(cityName, mayorUUID);
        setDirty();
    }

    /** Returns the city the player is mayor of, or null. */
    public String getMayorCity(String countryName, UUID playerUUID) {
        Country c = countries.get(countryName);
        if (c == null) return null;
        for (Map.Entry<String, UUID> e : c.cityMayors.entrySet())
            if (e.getValue().equals(playerUUID)) return e.getKey();
        return null;
    }

    /** Returns the city treasury balance. */
    public int getCityBalance(String countryName, String cityName) {
        Country c = countries.get(countryName);
        return c != null ? c.cityBalances.getOrDefault(cityName, 0) : 0;
    }

    // ── EMBASSIES ────────────────────────────────────────────────────────

    /**
     * Links an embassy (by block position) to a partner country.
     * Returns false if the country has no embassy block at that position.
     */
    public boolean linkEmbassy(String countryName, long blockPosLong, String partnerCountry) {
        Country c = countries.get(countryName);
        if (c == null || !c.embassyBlocks.contains(blockPosLong)) return false;
        c.embassyLinks.put(blockPosLong, partnerCountry);
        setDirty();
        return true;
    }

    /** Unlinks the embassy at the given block position. */
    public boolean unlinkEmbassy(String countryName, long blockPosLong) {
        Country c = countries.get(countryName);
        if (c == null) return false;
        boolean removed = c.embassyLinks.remove(blockPosLong) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Finds the country's embassy in the given chunk (first match). */
    public Long findEmbassyInChunk(String countryName, net.minecraft.world.level.ChunkPos chunk) {
        Country c = countries.get(countryName);
        if (c == null) return null;
        for (Long posLong : c.embassyBlocks) {
            if (new ChunkPos(BlockPos.of(posLong)).equals(chunk)) return posLong;
        }
        return null;
    }

    // ── TERRITORY TRADING ────────────────────────────────────────────────

    public void transferChunk(ChunkPos pos, String newCountry) {
        chunkOwners.put(pos, newCountry);
        chunkCity.remove(pos);
        setDirty();
    }

    // ── GETTERS ──────────────────────────────────────────────────────────

    public Country getCountry(String name)        { return countries.get(name); }
    public Map<String, Country> getCountries()    { return Collections.unmodifiableMap(countries); }
    public Country getCountryAt(ChunkPos pos)     { String n = chunkOwners.get(pos); return n!=null ? countries.get(n) : null; }
    public String getCountryNameAt(ChunkPos pos)  { return chunkOwners.get(pos); }
    public String getCityAt(ChunkPos pos)         { return chunkCity.get(pos); }

    public String getCountryByOwner(UUID uuid) {
        for (Country c : countries.values()) if (c.getRole(uuid) == CountryRole.LEADER) return c.getName();
        return null;
    }
    public String getPlayerCountry(UUID id) {
        for (Country c : countries.values()) if (c.getRole(id) != CountryRole.GUEST) return c.getName();
        return null;
    }

    public String getFlagUrl(String c)           { return countries.containsKey(c) ? countries.get(c).flagUrl : ""; }
    public void setFlagUrl(String c, String url) { if (countries.containsKey(c)) { countries.get(c).flagUrl = url; setDirty(); } }
    public int getBalance(String c)              { return countries.containsKey(c) ? countries.get(c).balance : 0; }
    public List<String> getCities(String c)      { return countries.containsKey(c) ? countries.get(c).cities : new ArrayList<>(); }
    public boolean hasCity(String c, String city){ return countries.containsKey(c) && countries.get(c).cities.contains(city); }

    public int getColorForChunk(ChunkPos pos) {
        String city = chunkCity.get(pos);
        if (city != null && !city.isEmpty()) {
            String cn = chunkOwners.get(pos);
            if (cn != null && countries.containsKey(cn))
                return countries.get(cn).cityColors.getOrDefault(city, 0xFFD700);
        }
        return chunkOwners.containsKey(pos) ? 0x00AA00 : 0;
    }

    public void syncToPlayer(ServerPlayer player) {
        forEachClaim((pos, color) -> ModNetworking.toPlayer(player, new SyncChunkPayload(pos, color)));
    }
    public void forEachClaim(java.util.function.BiConsumer<ChunkPos, Integer> action) {
        for (ChunkPos pos : chunkOwners.keySet()) action.accept(pos, getColorForChunk(pos));
    }

    // ── CREATION AND CLAIMS ──────────────────────────────────────────────

    private void claimAreaInternal(ChunkPos center, int radius, String country, String city) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos p = new ChunkPos(center.x+x, center.z+z);
                chunkOwners.put(p, country);
                if (city != null && !city.isEmpty()) chunkCity.put(p, city);
                else chunkCity.remove(p);
            }
        }
    }
    public void claimChunk(ChunkPos c, int r, String country, String city) { claimAreaInternal(c, r, country, city); setDirty(); }

    public static void createCountry(Level level, BlockPos center, Player owner, String name) {
        PoliticsManager m = get(level);
        if (m == null) return;
        if (m.countries.containsKey(name)) { owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists")); return; }
        if (m.getCountryByOwner(owner.getUUID()) != null) {
            owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists_error",
                m.getCountryByOwner(owner.getUUID())).withStyle(net.minecraft.ChatFormatting.RED)); return;
        }
        m.countries.put(name, new Country(name, owner.getUUID()));
        m.claimAreaInternal(new ChunkPos(center), 2, name, null);
        m.setDirty(); m.syncToPlayer((ServerPlayer) owner);
    }

    public void foundNewCity(BlockPos center, Player player, String cityName) {
        String cn = getCountryByOwner(player.getUUID());
        if (cn == null) return;
        claimAreaInternal(new ChunkPos(center), 1, cn, cityName);
        createCityEntry(cn, cityName);
        countries.get(cn).cityColors.put(cityName, 0xFFD700);
        countries.get(cn).cityBalances.put(cityName, 0);
        setDirty(); syncToPlayer((ServerPlayer) player);
    }

    // ── ECONOMY CYCLE ────────────────────────────────────────────────────

    public void processEconomyCycle(ServerLevel level) {
        for (Country country : countries.values()) {
            int chunks = 0;
            for (String o : chunkOwners.values()) if (o.equals(country.getName())) chunks++;

            // Tax block income goes to the city treasury inside a city, otherwise to the nation
            Map<String, Integer> thisCycleIncome = new HashMap<>();
            for (Long posLong : country.taxBlocks) {
                ChunkPos cp = new ChunkPos(BlockPos.of(posLong));
                String city = chunkCity.get(cp);
                if (city != null && country.cities.contains(city)) {
                    thisCycleIncome.merge(city, PoliticsConfig.get().incomePerTaxBlock, Integer::sum);
                } else {
                    country.balance += PoliticsConfig.get().incomePerTaxBlock;
                }
            }

            // Federal tax: a share of city income goes to the nation
            for (Map.Entry<String, Integer> e : thisCycleIncome.entrySet()) {
                String city    = e.getKey();
                int cycleIncome = e.getValue();
                int fedTax     = country.federalTaxRate > 0 ? cycleIncome * country.federalTaxRate / 100 : 0;
                country.cityBalances.merge(city, cycleIncome - fedTax, Integer::sum);
                country.balance += fedTax;
            }

            // Territory upkeep
            country.balance -= chunks * PoliticsConfig.get().upkeepPerChunk;
            if (country.balance < 0) country.balance = 0;
        }

        // Tribute payments (separate pass)
        for (Map.Entry<String, String[]> e : tributes.entrySet()) {
            Country payer    = countries.get(e.getKey());
            Country receiver = countries.get(e.getValue()[0]);
            int rate         = Integer.parseInt(e.getValue()[1]);
            if (payer == null || receiver == null) continue;
            int amount = payer.taxBlocks.size() * PoliticsConfig.get().incomePerTaxBlock * rate / 100;
            if (amount > 0) {
                payer.balance    = Math.max(0, payer.balance - amount);
                receiver.balance += amount;
            }
        }

        // ── Embassies: bonus income for active diplomatic links
        for (Country country : countries.values()) {
            for (String partner : country.embassyLinks.values()) {
                if (countries.containsKey(partner) && !isAtWar(country.getName(), partner)) {
                    country.balance += PoliticsConfig.get().embassyIncomeBonus;
                }
            }
        }

        // ── Vaults: cap the maximum balance
        for (Country country : countries.values()) {
            int maxBalance = PoliticsConfig.get().baseMaxBalance + country.vaultBlocks.size() * PoliticsConfig.get().vaultBalanceBonus;
            if (country.balance > maxBalance) country.balance = maxBalance;
        }

        setDirty();
    }

    // ── SECESSION ────────────────────────────────────────────────────────

    /**
     * A mayor splits their city off into a new country.
     * Returns false if the city is missing, the name is taken or no mayor is assigned.
     */
    public boolean secede(String oldCountryName, String cityName) {
        Country old = countries.get(oldCountryName);
        if (old == null || !old.cities.contains(cityName)) return false;

        UUID newLeaderUUID = old.cityMayors.get(cityName);
        if (newLeaderUUID == null) return false;
        if (countries.containsKey(cityName)) return false;

        // Collect the city's chunks
        List<ChunkPos> cityChunks = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet()) {
            if (e.getValue().equals(oldCountryName) && cityName.equals(chunkCity.get(e.getKey())))
                cityChunks.add(e.getKey());
        }

        // Collect tax blocks inside those chunks
        Set<Long> cityChunkLongs = new HashSet<>();
        for (ChunkPos cp : cityChunks) cityChunkLongs.add(cp.toLong());

        List<Long> transferTax = new ArrayList<>();
        old.taxBlocks.removeIf(posLong -> {
            long cpLong = new ChunkPos(BlockPos.of(posLong)).toLong();
            if (cityChunkLongs.contains(cpLong)) { transferTax.add(posLong); return true; }
            return false;
        });

        // Create the new country
        Country newCountry = new Country(cityName, newLeaderUUID);
        newCountry.balance = old.cityBalances.getOrDefault(cityName, 0);
        newCountry.taxBlocks.addAll(transferTax);
        countries.put(cityName, newCountry);

        // Transfer the chunks
        for (ChunkPos pos : cityChunks) {
            chunkOwners.put(pos, cityName);
            chunkCity.remove(pos);
            ModNetworking.toAll(new SyncChunkPayload(pos, getColorForChunk(pos)));
        }

        // Clean up the old country
        old.setRole(newLeaderUUID, CountryRole.GUEST);
        old.cities.remove(cityName);
        old.cityBalances.remove(cityName);
        old.cityMayors.remove(cityName);
        old.cityColors.remove(cityName);

        setDirty();
        return true;
    }

    // ── CITIES ───────────────────────────────────────────────────────────

    public void createCityEntry(String cn, String city) {
        Country c = countries.get(cn);
        if (c != null && !c.cities.contains(city)) c.cities.add(city);
    }
    public void createCity(String cn, String city)   { createCityEntry(cn, city); setDirty(); }
    public void setCapital(String cn, String city)   {
        Country c = countries.get(cn);
        if (c != null && c.cities.contains(city)) { c.capital = city; c.cityColors.put(city, 0xFFD700); setDirty(); }
    }

    // ── COUNTRY DELETION ─────────────────────────────────────────────────

    public boolean deleteCountry(String name) {
        if (!countries.containsKey(name)) return false;
        List<ChunkPos> toRemove = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet())
            if (e.getValue().equals(name)) toRemove.add(e.getKey());
        for (ChunkPos pos : toRemove) {
            chunkOwners.remove(pos); chunkCity.remove(pos);
            ModNetworking.toAll(new SyncChunkPayload(pos, 0));
        }
        diplomacy.remove(name);
        for (Map<String, DiplomacyStatus> rel : diplomacy.values()) rel.remove(name);
        warCooldowns.remove(name);
        tributes.remove(name);
        tributes.entrySet().removeIf(e -> e.getValue()[0].equals(name));
        // Listings of a dissolved country are removed from the market
        listings.removeIf(l -> l.seller.equals(name));
        countries.remove(name);
        setDirty();
        return true;
    }

    public boolean renameCountry(String old, String nw)        { return false; }
    public boolean renameCity(String c, String old, String nw) { return false; }
    public void collectTaxes(ServerLevel level)                {}
}

package net.krona.politicsmod;

import net.krona.politicsmod.config.PoliticsConfig;

import net.krona.politicsmod.network.SyncChunkPayload;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.Loan;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.MarketListing;
import net.krona.politicsmod.politics.Plot;
import net.krona.politicsmod.protection.ProtectionFlag;
import net.krona.politicsmod.economy.Wallet;
import net.krona.politicsmod.economy.WalletService;
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
    private final Map<UUID, Wallet>                         wallets      = new HashMap<>();
    private final Map<ChunkPos, Plot>                       plots        = new HashMap<>();
    private final Set<UUID>                                 guideGiven   = new HashSet<>();
    private final List<NewsEntry>                           news         = new ArrayList<>();
    private final List<Loan>                                loans        = new ArrayList<>();

    /** Bumped whenever a chunk changes hands; web maps redraw when it changes. Not saved. */
    private int territoryVersion = 0;

    /**
     * Version of the saved data layout. Bump it when a change needs migrating old worlds.
     * 1 = up to 0.3 (not written), 2 = 0.4 (wallets, salaries, plots, protection flags),
     * 3 = 0.4 (news, loans, laws, elections).
     */
    public static final int FORMAT_VERSION = 3;

    public PoliticsManager() {}

    // ── INSTANCE ─────────────────────────────────────────────────────────

    /**
     * The server's political data. There is one set of countries for the whole server, stored with the
     * Overworld: before 0.4 every dimension had its own copy, so in the Nether players had "no country"
     * and could found a second, separate one.
     */
    public static PoliticsManager get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PoliticsManager::new, PoliticsManager::load, null),
                "politicsmod_data");
        }
        return null;
    }

    /** Countries own land only in the Overworld. */
    public static boolean hasTerritory(Level level) {
        return level != null && level.dimension() == Level.OVERWORLD;
    }

    /**
     * For rules that depend on where something is (protection, blocks, claims): the manager in the
     * Overworld, null in other dimensions, where chunk coordinates don't belong to any country.
     */
    public static PoliticsManager at(Level level) {
        return hasTerritory(level) ? get(level) : null;
    }

    /** For commands about the chunk the player stands in; outside the Overworld tells them why not and returns null. */
    public static PoliticsManager here(ServerPlayer player) {
        if (!hasTerritory(player.level())) {
            player.sendSystemMessage(Component.translatable("message.politicsmod.overworld_only").withStyle(net.minecraft.ChatFormatting.RED));
            return null;
        }
        return get(player.level());
    }

    // ── NBT SAVE ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("formatVersion", FORMAT_VERSION);

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

        ListTag walletList = new ListTag();
        for (Map.Entry<UUID, Wallet> e : wallets.entrySet()) {
            CompoundTag w = e.getValue().save();
            w.putUUID("player", e.getKey());
            walletList.add(w);
        }
        tag.put("wallets", walletList);

        ListTag plotList = new ListTag();
        for (Plot p : plots.values()) plotList.add(p.save());
        tag.put("plots", plotList);

        ListTag guideList = new ListTag();
        for (UUID id : guideGiven) {
            CompoundTag g = new CompoundTag();
            g.putUUID("player", id);
            guideList.add(g);
        }
        tag.put("guideGiven", guideList);

        ListTag newsList = new ListTag();
        for (NewsEntry n : news) newsList.add(n.save());
        tag.put("news", newsList);

        ListTag loanList = new ListTag();
        for (Loan l : loans) loanList.add(l.save());
        tag.put("loans", loanList);

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

        ListTag walletList = tag.getList("wallets", Tag.TAG_COMPOUND);
        for (int i = 0; i < walletList.size(); i++) {
            CompoundTag w = walletList.getCompound(i);
            if (w.hasUUID("player")) m.wallets.put(w.getUUID("player"), Wallet.load(w));
        }

        ListTag plotList = tag.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotList.size(); i++) {
            Plot p = Plot.load(plotList.getCompound(i));
            m.plots.put(p.pos, p);
        }

        ListTag guideList = tag.getList("guideGiven", Tag.TAG_COMPOUND);
        for (int i = 0; i < guideList.size(); i++) {
            if (guideList.getCompound(i).hasUUID("player")) m.guideGiven.add(guideList.getCompound(i).getUUID("player"));
        }

        ListTag newsList = tag.getList("news", Tag.TAG_COMPOUND);
        for (int i = 0; i < newsList.size(); i++) m.news.add(NewsEntry.load(newsList.getCompound(i)));

        ListTag loanList = tag.getList("loans", Tag.TAG_COMPOUND);
        for (int i = 0; i < loanList.size(); i++) m.loans.add(Loan.load(loanList.getCompound(i)));
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

    public MarketListing addListing(String seller, UUID listedBy, String sellerName, boolean personal,
                                    net.minecraft.world.item.ItemStack stack, int price) {
        MarketListing listing = new MarketListing(nextListingId++, seller, listedBy, sellerName, personal, stack, price,
                System.currentTimeMillis());
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

    /** State listings of a country. */
    public int countListings(String seller) {
        int n = 0;
        for (MarketListing l : listings) if (!l.personal && l.seller.equals(seller)) n++;
        return n;
    }

    /** Personal listings of a player. */
    public int countPersonalListings(UUID player) {
        int n = 0;
        for (MarketListing l : listings) if (l.personal && l.listedBy.equals(player)) n++;
        return n;
    }

    // ── WALLETS ──────────────────────────────────────────────────────────

    /** The player's wallet; created with the configured starting balance on first access. */
    public Wallet getWallet(UUID player) {
        return wallets.computeIfAbsent(player, id -> {
            setDirty();
            return new Wallet(PoliticsConfig.get().walletStartBalance);
        });
    }

    // ── PROTECTION FLAGS ─────────────────────────────────────────────────

    /** Effective flag value at a chunk: city override, then country override, then the server default. */
    public boolean isAllowed(ChunkPos pos, ProtectionFlag flag) {
        String owner = chunkOwners.get(pos);
        if (owner == null) return true;
        Country country = countries.get(owner);
        if (country == null) return true;
        String city = chunkCity.get(pos);
        if (city != null) {
            Map<ProtectionFlag, Boolean> cf = country.cityFlags.get(city);
            if (cf != null && cf.containsKey(flag)) return cf.get(flag);
        }
        Boolean value = country.flags.get(flag);
        return value != null ? value : flag.defaultValue();
    }

    // ── PLOTS ────────────────────────────────────────────────────────────

    /**
     * The plot at a chunk, or null. Plots whose chunk no longer belongs to the same city
     * (sold, unclaimed, seceded, country dissolved) are dropped here.
     */
    public Plot getPlot(ChunkPos pos) {
        Plot plot = plots.get(pos);
        if (plot == null) return null;
        if (!plot.country.equals(chunkOwners.get(pos)) || !plot.city.equals(chunkCity.get(pos))) {
            plots.remove(pos);
            setDirty();
            return null;
        }
        return plot;
    }

    public Plot createPlot(ChunkPos pos) {
        Plot plot = new Plot(pos, chunkOwners.get(pos), chunkCity.get(pos));
        plots.put(pos, plot);
        setDirty();
        return plot;
    }

    public void removePlot(ChunkPos pos) {
        if (plots.remove(pos) != null) setDirty();
    }

    public int countOwnedPlots(UUID player) {
        int n = 0;
        for (Plot p : plots.values()) if (player.equals(p.owner)) n++;
        return n;
    }

    public List<Plot> getPlots() {
        return new ArrayList<>(plots.values());
    }

    // ── NEWS ─────────────────────────────────────────────────────────────

    /**
     * Adds a line to the news feed. countries = the countries it concerns (used to filter the feed);
     * arguments starting with '#' are translation keys.
     */
    public void postNews(String key, List<String> countries, String... args) {
        news.add(new NewsEntry(System.currentTimeMillis(), key, List.of(args), List.copyOf(countries)));
        int max = PoliticsConfig.get().newsMaxEntries;
        while (news.size() > max) news.remove(0);
        setDirty();
    }

    /** Oldest first. */
    public List<NewsEntry> getNews() { return Collections.unmodifiableList(news); }

    // ── LOANS AND DEBT ───────────────────────────────────────────────────

    public List<Loan> getLoans() { return Collections.unmodifiableList(loans); }

    public void addLoan(Loan loan) { loans.add(loan); setDirty(); }

    /** Total a country still owes on its loans. */
    public int getDebt(String country) {
        long total = 0;
        for (Loan l : loans) if (l.borrower.equals(country)) total += l.remaining;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public boolean isBankrupt(String country) {
        Country c = countries.get(country);
        return c != null && c.isBankrupt();
    }

    /** Takes up to amount from a treasury without going below the debt limit; returns what was taken. */
    public static int charge(Country country, int amount) {
        long before = country.balance;
        long after = Math.max(-(long) PoliticsConfig.get().maxDebt, before - amount);
        if (after > before) after = before; // already past the limit: take nothing more
        country.balance = (int) after;
        return (int) (before - after);
    }

    // ── GUIDE ────────────────────────────────────────────────────────────

    /** True the first time it is called for a player. */
    public boolean markGuideGiven(UUID player) {
        boolean first = guideGiven.add(player);
        if (first) setDirty();
        return first;
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

    /**
     * Gives a chunk to another country (sale, siege). Tax blocks inside move with it; the old owner's
     * embassies, radars and vaults there stop counting. Everyone online sees the new border.
     */
    public void transferChunk(ChunkPos pos, String newCountry) {
        Country old = getCountryAt(pos);
        Country gainer = countries.get(newCountry);
        if (old != null && gainer != null && old != gainer) {
            old.taxBlocks.removeIf(p -> {
                if (!new ChunkPos(BlockPos.of(p)).equals(pos)) return false;
                gainer.taxBlocks.add(p);
                return true;
            });
            old.embassyBlocks.removeIf(p -> {
                boolean inside = new ChunkPos(BlockPos.of(p)).equals(pos);
                if (inside) old.embassyLinks.remove(p);
                return inside;
            });
            old.radarBlocks.removeIf(p -> new ChunkPos(BlockPos.of(p)).equals(pos));
            old.vaultBlocks.removeIf(p -> new ChunkPos(BlockPos.of(p)).equals(pos));
        }
        chunkOwners.put(pos, newCountry);
        chunkCity.remove(pos);
        territoryChanged();
        ModNetworking.toAll(chunkPayload(pos));
    }

    /** Current owner, city and colour of a chunk for clients. */
    public SyncChunkPayload chunkPayload(ChunkPos pos) {
        String owner = chunkOwners.get(pos);
        String city = chunkCity.get(pos);
        return new SyncChunkPayload(pos, getColorForChunk(pos), owner == null ? "" : owner, city == null ? "" : city);
    }

    public void syncChunksToAll(Iterable<ChunkPos> chunks) {
        for (ChunkPos pos : chunks) ModNetworking.toAll(chunkPayload(pos));
    }

    public int getTerritoryVersion() { return territoryVersion; }

    private void territoryChanged() {
        territoryVersion++;
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
        for (ChunkPos pos : chunkOwners.keySet()) ModNetworking.toPlayer(player, chunkPayload(pos));
    }

    /** All chunks of one country. */
    public List<ChunkPos> getChunksOf(String country) {
        List<ChunkPos> list = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet()) if (e.getValue().equals(country)) list.add(e.getKey());
        return list;
    }

    /** Every claimed chunk and its owner. */
    public Map<ChunkPos, String> getClaims() { return Collections.unmodifiableMap(chunkOwners); }
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
    public void claimChunk(ChunkPos c, int r, String country, String city) {
        claimAreaInternal(c, r, country, city);
        territoryChanged();
        List<ChunkPos> changed = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) changed.add(new ChunkPos(c.x + x, c.z + z));
        syncChunksToAll(changed);
    }

    /** Country and city names: 2-24 letters, digits, spaces, '_' or '-'. */
    public static boolean isValidName(String name) {
        return name != null && name.strip().equals(name) && name.matches("[\\p{L}\\p{N} _-]{2,24}");
    }

    public static void createCountry(Level level, BlockPos center, Player owner, String name) {
        PoliticsManager m = get(level);
        if (m == null) return;
        if (!hasTerritory(level)) { fail(owner, "message.politicsmod.overworld_only"); return; }
        if (!isValidName(name)) { fail(owner, "message.politicsmod.found.invalid_name"); return; }
        if (m.countries.containsKey(name)) { fail(owner, "message.politicsmod.country_exists"); return; }
        String current = m.getPlayerCountry(owner.getUUID());
        if (current != null) {
            owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists_error", current)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        // The founding area must be free: founding next to a neighbour must not take their land
        ChunkPos c = new ChunkPos(center);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (m.chunkOwners.containsKey(new ChunkPos(c.x + x, c.z + z))) {
                    fail(owner, "message.politicsmod.found.area_taken");
                    return;
                }
            }
        }
        m.countries.put(name, new Country(name, owner.getUUID()));
        m.claimChunk(c, 2, name, null);
        m.postNews("news.politicsmod.country_founded", List.of(name), name, owner.getGameProfile().getName());
        net.krona.politicsmod.network.SyncFlagsPayload.broadcast(m);
        owner.sendSystemMessage(Component.translatable("message.politicsmod.found.country_success", name)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }

    /** A city covers the stone's chunk and its neighbours, but only land the country already owns. */
    public void foundNewCity(BlockPos center, Player player, String cityName) {
        if (!hasTerritory(player.level())) { fail(player, "message.politicsmod.overworld_only"); return; }
        String cn = getCountryByOwner(player.getUUID());
        if (cn == null) { fail(player, "message.politicsmod.claim.leader_only"); return; }
        Country country = countries.get(cn);
        if (!isValidName(cityName)) { fail(player, "message.politicsmod.found.invalid_name"); return; }
        if (country.cities.contains(cityName)) { fail(player, "message.politicsmod.found.city_exists"); return; }

        ChunkPos c = new ChunkPos(center);
        if (!cn.equals(chunkOwners.get(c)) || chunkCity.containsKey(c)) {
            fail(player, "message.politicsmod.claim.city_on_claimed_only");
            return;
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                ChunkPos p = new ChunkPos(c.x + x, c.z + z);
                if (cn.equals(chunkOwners.get(p)) && !chunkCity.containsKey(p)) chunkCity.put(p, cityName);
            }
        }
        createCityEntry(cn, cityName);
        country.cityColors.put(cityName, 0xFFD700);
        country.cityBalances.put(cityName, 0);
        territoryChanged();
        List<ChunkPos> changed = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) changed.add(new ChunkPos(c.x + x, c.z + z));
        syncChunksToAll(changed);
        postNews("news.politicsmod.city_founded", List.of(cn), cityName, cn);
        player.sendSystemMessage(Component.translatable("message.politicsmod.found.city_success", cityName)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }

    private static void fail(Player player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.RED));
    }

    // ── ECONOMY CYCLE ────────────────────────────────────────────────────

    public void processEconomyCycle(ServerLevel level) {
        for (Country country : countries.values()) {
            int chunks = 0;
            for (String o : chunkOwners.values()) if (o.equals(country.getName())) chunks++;

            // Tax block income goes to the city treasury inside a city, otherwise to the nation
            int perBlock = taxBlockIncome(country);
            Map<String, Integer> thisCycleIncome = new HashMap<>();
            for (Long posLong : country.taxBlocks) {
                ChunkPos cp = new ChunkPos(BlockPos.of(posLong));
                String city = chunkCity.get(cp);
                if (city != null && country.cities.contains(city)) {
                    thisCycleIncome.merge(city, perBlock, Integer::sum);
                } else {
                    country.balance += perBlock;
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

            // Territory upkeep: the treasury can go into debt, down to the configured limit
            charge(country, chunks * PoliticsConfig.get().upkeepPerChunk);
        }

        // Tribute payments (separate pass)
        for (Map.Entry<String, String[]> e : tributes.entrySet()) {
            Country payer    = countries.get(e.getKey());
            Country receiver = countries.get(e.getValue()[0]);
            int rate         = Integer.parseInt(e.getValue()[1]);
            if (payer == null || receiver == null) continue;
            int amount = payer.taxBlocks.size() * PoliticsConfig.get().incomePerTaxBlock * rate / 100;
            if (amount > 0) {
                receiver.balance += charge(payer, amount);
            }
        }

        // ── Embassies: bonus income for active diplomatic links
        for (Country country : countries.values()) {
            for (String partner : country.embassyLinks.values()) {
                if (countries.containsKey(partner) && !isAtWar(country.getName(), partner)) {
                    country.balance += embassyIncome(country);
                }
            }
        }

        // ── Salaries and income tax
        for (Country country : countries.values()) {
            WalletService.paySalaries(this, country, level.getServer());
        }

        // ── Plots: rent, and owners who left the country
        net.krona.politicsmod.economy.PlotService.processCycle(this, level.getServer());

        // ── Loans: one installment each, even into debt
        for (java.util.Iterator<Loan> it = loans.iterator(); it.hasNext(); ) {
            Loan loan = it.next();
            Country borrower = countries.get(loan.borrower);
            Country lender = countries.get(loan.lender);
            if (borrower == null || lender == null) { it.remove(); continue; }
            int paid = charge(borrower, Math.min(loan.installment, loan.remaining));
            lender.balance += paid;
            loan.remaining -= paid;
            if (loan.remaining <= 0) {
                it.remove();
                postNews("news.politicsmod.loan_repaid", List.of(loan.borrower, loan.lender), loan.borrower, loan.lender);
            }
        }

        // ── Vaults: cap the maximum balance
        for (Country country : countries.values()) {
            int maxBalance = PoliticsConfig.get().baseMaxBalance + country.vaultBlocks.size() * PoliticsConfig.get().vaultBalanceBonus;
            if (country.balance > maxBalance) country.balance = maxBalance;
        }

        // ── Bankruptcy: announce going under and getting out once
        for (Country country : countries.values()) {
            boolean now = country.isBankrupt();
            if (now && !country.bankrupt) {
                postNews("news.politicsmod.bankrupt", List.of(country.getName()), country.getName());
            } else if (!now && country.bankrupt) {
                postNews("news.politicsmod.recovered", List.of(country.getName()), country.getName());
            }
            country.bankrupt = now;
        }

        setDirty();
    }

    /** Income of one tax block for a country; martial law lowers it. */
    public static int taxBlockIncome(Country country) {
        int income = PoliticsConfig.get().incomePerTaxBlock;
        if (country.hasLaw(Law.MARTIAL_LAW)) income = income * (100 - PoliticsConfig.get().martialLawIncomePenaltyPercent) / 100;
        return income;
    }

    /** Income of one active embassy for a country; free trade raises it. */
    public static int embassyIncome(Country country) {
        int income = PoliticsConfig.get().embassyIncomeBonus;
        if (country.hasLaw(Law.FREE_TRADE)) income = income * (100 + PoliticsConfig.get().freeTradeEmbassyBonusPercent) / 100;
        return income;
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
        }
        territoryChanged();
        syncChunksToAll(cityChunks);

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
        }
        territoryChanged();
        syncChunksToAll(toRemove);
        loans.removeIf(l -> l.lender.equals(name) || l.borrower.equals(name));
        diplomacy.remove(name);
        for (Map<String, DiplomacyStatus> rel : diplomacy.values()) rel.remove(name);
        warCooldowns.remove(name);
        tributes.remove(name);
        tributes.entrySet().removeIf(e -> e.getValue()[0].equals(name));
        // Listings of a dissolved country are removed from the market
        plots.values().removeIf(p -> p.country.equals(name));
        // State listings disappear with the country; personal listings stay, they belong to players
        listings.removeIf(l -> !l.personal && l.seller.equals(name));
        countries.remove(name);
        postNews("news.politicsmod.country_dissolved", List.of(name), name);
        net.krona.politicsmod.network.SyncFlagsPayload.broadcast(this);
        setDirty();
        return true;
    }

    public boolean renameCountry(String old, String nw)        { return false; }
    public boolean renameCity(String c, String old, String nw) { return false; }
    public void collectTaxes(ServerLevel level)                {}
}

package net.krona.politicsmod.network;

import net.krona.politicsmod.commands.StateCommands;
import net.krona.politicsmod.politics.Election;
import net.krona.politicsmod.politics.ElectionService;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.Loan;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.RoleAction;
import net.krona.politicsmod.war.SiegeService;
import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.ModCommands;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.EconomySummary;
import net.krona.politicsmod.protection.ProtectionFlag;
import net.krona.politicsmod.realm.Breakdown;
import net.krona.politicsmod.realm.ModifierType;
import net.krona.politicsmod.realm.Realm;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the dashboard tabs show (server -> client).
 * Sent when the menu opens and on RequestCountryDetailsPayload after GUI actions.
 */
public record CountryDetailsPayload(
        List<Member> members,
        List<Relation> relations,
        String pendingFrom,     // "" when there is no incoming proposal
        String pendingType,     // "ALLIANCE" | "NEUTRAL"
        int warCost,
        int secedeThreshold,
        int permissions,        // RoleAction mask of the viewer
        EconomySummary economy,
        List<FlagOverride> flags,
        int flagDefaults,       // bit per ProtectionFlag ordinal: the server default
        String capital,
        List<City> cities,
        int laws,               // bit per Law ordinal
        int lawCooldown,        // seconds until laws may change again
        ElectionInfo election,
        List<SiegeInfo> sieges,
        List<LoanInfo> loans,
        LoanOfferInfo loanOffer, // lender "" when there is none
        List<News> news,        // newest last
        int siegeCost,
        int maxLoanInterest,
        int maxLoanCycles,
        net.krona.politicsmod.economy.LandPrices.Rules landPrices,
        boolean settlementsEnabled, // MineColonies or Millénaire is installed and switched on
        int annexReputation,        // reputation a citizen needs in a village before it can be annexed
        List<SettlementInfo> settlements,
        RealmInfo realm,
        List<ChunkValue> nonCore,   // own chunks that are not yet cores, value = cycles left
        List<Long> lostCores,       // chunks the country lost while they were its cores
        List<ChunkValue> chunkIncome, // own chunks with tax blocks, value = income per cycle
        List<LedgerRow> ledger,     // every country, for the Ledger tab and the stability map
        List<History> history,      // daily samples; treasury and net only for the viewer's own country
        List<net.krona.politicsmod.realm.Alerts.Alert> alerts
) implements CustomPacketPayload {

    /**
     * Stability, legitimacy and prestige with what moves them; modifiers by ModifierType ordinal.
     * Legitimacy and prestige are sent ×10; the legitimacy trend is in tenths.
     */
    public record RealmInfo(boolean enabled, int stability, int progress, int perLevel, Breakdown trend,
                            int legitimacy10, Breakdown legitimacyTrend, int prestige10, int overextension,
                            int stabilizeCost, int stabilizeCooldown, int coreCycles, int maxDevelopment,
                            int developmentTaxPercent, int unrestSecede, int nonCoreTaxPercent, List<Breakdown> modifiers) {
        public Breakdown modifier(ModifierType type) {
            return type.ordinal() < modifiers.size() ? modifiers.get(type.ordinal()) : Breakdown.EMPTY;
        }
    }

    public record ChunkValue(long pos, int value) {
    }

    public record LedgerRow(String country, int chunks, int cities, int citizens, int development, int stability,
                            int prestige, int legitimacy) {
    }

    public record History(String country, List<int[]> samples) {
    }

    /** A colony or village of the country, or one it may annex. Kind and status are the Settlement enum ordinals. */
    public record SettlementInfo(String key, int kind, String name, int chunkX, int chunkZ, int population, int mood,
                                 String owner, int status, int tax) {
        public net.krona.politicsmod.integration.settlement.Settlement.Kind kindEnum() {
            return net.krona.politicsmod.integration.settlement.Settlement.Kind.values()[kind];
        }

        public net.krona.politicsmod.integration.settlement.Settlement.Status statusEnum() {
            return net.krona.politicsmod.integration.settlement.Settlement.Status.values()[status];
        }
    }

    /** A protection flag set explicitly; target "" = the whole country, otherwise a city name. */
    public record FlagOverride(String target, String flag, boolean value) {
    }

    public record Member(String name, String role, boolean online, int salary) {
    }

    public record Relation(String country, String status) {
    }

    /** nextDevelopmentCost -1 = at the limit; taxPercent = what development and unrest add to its tax blocks. */
    public record City(String name, String mayor, int balance, int chunks, int development, int boughtDevelopment,
                       int nextDevelopmentCost, int nonCore, Breakdown unrest, int taxPercent) {
        public int unrestValue() {
            return Math.max(0, Math.min(100, unrest.total()));
        }
    }

    public record Candidate(String name, int votes) {
    }

    /** type "" = no election running. */
    public record ElectionInfo(String type, String city, int secondsLeft, List<Candidate> candidates, String myVote,
                               boolean candidate, int petition, int petitionNeeded, boolean signed, int cooldown) {
    }

    public record SiegeInfo(String attacker, String defender, int x, int z, int percent, int secondsLeft) {
    }

    /** owe = this country is the borrower. */
    public record LoanInfo(String other, int remaining, int installment, boolean owe) {
    }

    public record LoanOfferInfo(String lender, int amount, int interest, int cycles) {
    }

    public record News(long time, String key, List<String> args) {
        public NewsEntry entry() {
            return new NewsEntry(time, key, args, List.of());
        }
    }

    private static final int NEWS_SENT = 40;

    public static final Type<CountryDetailsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "country_details"));

    public static final StreamCodec<FriendlyByteBuf, CountryDetailsPayload> CODEC =
            StreamCodec.of(CountryDetailsPayload::write, CountryDetailsPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CountryDetailsPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> ClientPoliticsData.setCountryDetails(payload));
    }

    public boolean hasLaw(Law law) {
        return (laws & (1 << law.ordinal())) != 0;
    }

    // ── Server-side build ────────────────────────────────────────────────

    /** Returns null if the player is not in a country. */
    public static CountryDetailsPayload build(ServerPlayer player) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return null;
        String countryName = mgr.getPlayerCountry(player.getUUID());
        if (countryName == null) return null;
        Country country = mgr.getCountry(countryName);
        MinecraftServer server = player.getServer();
        UUID me = player.getUUID();

        List<Member> members = new ArrayList<>();
        for (Map.Entry<UUID, CountryRole> e : country.getMembers().entrySet()) {
            ServerPlayer online = server != null ? server.getPlayerList().getPlayer(e.getKey()) : null;
            String name = online != null ? online.getGameProfile().getName() : nameOf(server, e.getKey());
            members.add(new Member(name, e.getValue().name(), online != null,
                    country.salaries.getOrDefault(e.getKey(), 0)));
        }
        // Leader -> mayors -> citizens, then by name
        members.sort(Comparator.comparingInt((Member m) -> -CountryRole.valueOf(m.role()).getLevel())
                .thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER));

        List<Relation> relations = new ArrayList<>();
        for (String other : mgr.getCountries().keySet()) {
            if (!other.equals(countryName)) {
                relations.add(new Relation(other, mgr.getDiplomacy(countryName, other).name()));
            }
        }
        relations.sort(Comparator.comparing(Relation::country, String.CASE_INSENSITIVE_ORDER));

        String[] pending = ModCommands.getPendingDiplomacy(countryName);

        Map<String, Realm.CityStats> cityStats = Realm.cities(mgr, country);
        List<City> cities = new ArrayList<>();
        for (String city : country.cities) {
            UUID mayor = country.cityMayors.get(city);
            Realm.CityStats s = cityStats.get(city);
            cities.add(new City(city, mayor == null ? "" : nameOf(server, mayor),
                    country.cityBalances.getOrDefault(city, 0), s.chunks(), s.development(), s.bought(),
                    Realm.developmentCost(country, city), s.nonCore(), s.unrest(), Realm.cityTaxPercent(s)));
        }

        int laws = 0;
        for (Law law : country.laws) laws |= 1 << law.ordinal();
        long lawCd = country.lawChangedAt + PoliticsConfig.get().lawCooldownMs() - System.currentTimeMillis();

        List<SiegeInfo> sieges = new ArrayList<>();
        for (SiegeService.Siege s : SiegeService.of(countryName)) {
            sieges.add(new SiegeInfo(s.attacker, s.defender, s.pos.x, s.pos.z, s.percent(), s.secondsLeft()));
        }

        List<LoanInfo> loans = new ArrayList<>();
        for (Loan l : mgr.getLoans()) {
            if (l.borrower.equals(countryName)) loans.add(new LoanInfo(l.lender, l.remaining, l.installment, true));
            else if (l.lender.equals(countryName)) loans.add(new LoanInfo(l.borrower, l.remaining, l.installment, false));
        }
        StateCommands.LoanOffer offer = StateCommands.getLoanOffer(countryName);
        LoanOfferInfo offerInfo = offer == null ? new LoanOfferInfo("", 0, 0, 0)
                : new LoanOfferInfo(offer.lender(), offer.amount(), offer.interest(), offer.cycles());

        List<NewsEntry> allNews = mgr.getNews();
        List<News> news = new ArrayList<>();
        for (int i = Math.max(0, allNews.size() - NEWS_SENT); i < allNews.size(); i++) {
            NewsEntry n = allNews.get(i);
            news.add(new News(n.time(), n.key(), n.args()));
        }

        PoliticsConfig cfg = PoliticsConfig.get();
        EconomySummary economy = EconomySummary.of(mgr, countryName);
        Breakdown trend = Realm.stabilityTrend(mgr, country, cityStats);
        return new CountryDetailsPayload(
                members,
                relations,
                pending != null ? pending[0] : "",
                pending != null ? pending[1] : "",
                cfg.warCost,
                cfg.secedeThresholdPercent,
                RoleAction.mask(country.getRole(me)),
                economy,
                flagOverrides(country),
                defaultFlagMask(),
                country.capital == null ? "" : country.capital,
                cities,
                laws,
                (int) Math.max(0, lawCd / 1000),
                electionInfo(country, me),
                sieges,
                loans,
                offerInfo,
                news,
                cfg.siegeCost,
                cfg.maxLoanInterestPercent,
                cfg.maxLoanCycles,
                net.krona.politicsmod.economy.LandPrices.Rules.of(country),
                net.krona.politicsmod.integration.settlement.Settlements.enabled(),
                cfg.villageAnnexReputation,
                settlementInfos(mgr, countryName),
                realmInfo(mgr, country, trend),
                nonCore(mgr, countryName),
                lostCores(mgr, countryName),
                chunkIncome(mgr, country, cityStats),
                ledger(mgr),
                history(mgr, countryName),
                net.krona.politicsmod.realm.Alerts.of(mgr, country, me, economy, cityStats, trend));
    }

    private static RealmInfo realmInfo(PoliticsManager mgr, Country c, Breakdown trend) {
        PoliticsConfig cfg = PoliticsConfig.get();
        List<Breakdown> modifiers = new ArrayList<>();
        for (ModifierType type : ModifierType.values()) modifiers.add(Realm.modifiers(c, type));
        return new RealmInfo(Realm.enabled(), c.stability, c.stabilityProgress, cfg.stabilityProgressPerLevel, trend,
                Math.round(c.legitimacy * 10), Realm.legitimacyTrend(c), Math.round(c.prestige * 10),
                Realm.overextension(mgr, c.getName()), Realm.stabilizeCost(c), (int) (Realm.stabilizeCooldown(c) / 1000),
                cfg.coreCycles, cfg.maxDevelopment, cfg.developmentTaxPercent, cfg.unrestSecedeThreshold,
                cfg.nonCoreTaxPercent, modifiers);
    }

    private static List<ChunkValue> nonCore(PoliticsManager mgr, String countryName) {
        List<ChunkValue> list = new ArrayList<>();
        for (ChunkPos pos : mgr.getChunksOf(countryName)) {
            if (!mgr.isCore(pos)) list.add(new ChunkValue(pos.toLong(), mgr.coreCyclesLeft(pos)));
        }
        return list;
    }

    private static List<Long> lostCores(PoliticsManager mgr, String countryName) {
        List<Long> list = new ArrayList<>();
        for (ChunkPos pos : mgr.getLostCores(countryName)) list.add(pos.toLong());
        return list;
    }

    private static List<ChunkValue> chunkIncome(PoliticsManager mgr, Country c, Map<String, Realm.CityStats> cityStats) {
        Realm.TaxRates rates = Realm.taxRates(c, cityStats);
        Map<Long, Integer> byChunk = new HashMap<>();
        for (Long posLong : c.taxBlocks) {
            ChunkPos pos = new ChunkPos(net.minecraft.core.BlockPos.of(posLong));
            byChunk.merge(pos.toLong(), Realm.taxBlockIncome(mgr, pos, rates), Integer::sum);
        }
        List<ChunkValue> list = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : byChunk.entrySet()) list.add(new ChunkValue(e.getKey(), e.getValue()));
        return list;
    }

    private static List<LedgerRow> ledger(PoliticsManager mgr) {
        List<LedgerRow> rows = new ArrayList<>();
        for (Country c : mgr.getCountries().values()) {
            rows.add(new LedgerRow(c.getName(), mgr.getChunksOf(c.getName()).size(), c.cities.size(), c.getMembers().size(),
                    Realm.totalDevelopment(mgr, c), c.stability, Math.round(c.prestige), Math.round(c.legitimacy)));
        }
        return rows;
    }

    /** Treasuries of other countries are their own business: their samples come without money. */
    private static List<History> history(PoliticsManager mgr, String mine) {
        List<History> list = new ArrayList<>();
        for (String name : mgr.getCountries().keySet()) {
            List<int[]> samples = new ArrayList<>();
            for (int[] s : mgr.getHistory(name)) {
                int[] copy = java.util.Arrays.copyOf(s, Realm.H_SIZE);
                if (!name.equals(mine)) {
                    copy[Realm.H_TREASURY] = 0;
                    copy[Realm.H_NET] = 0;
                }
                samples.add(copy);
            }
            if (!samples.isEmpty()) list.add(new History(name, samples));
        }
        return list;
    }

    private static List<SettlementInfo> settlementInfos(PoliticsManager mgr, String countryName) {
        List<SettlementInfo> list = new ArrayList<>();
        for (net.krona.politicsmod.integration.settlement.Settlement s
                : net.krona.politicsmod.integration.settlement.Settlements.of(mgr.overworld(), mgr, countryName)) {
            list.add(new SettlementInfo(s.key(), s.kind().ordinal(), s.name(), s.center().x, s.center().z, s.population(),
                    s.mood(), s.owner(), s.status().ordinal(), s.tax()));
        }
        return list;
    }

    private static ElectionInfo electionInfo(Country country, UUID me) {
        int petition = country.petition.size();
        int needed = ElectionService.petitionNeeded(country);
        boolean signed = country.petition.contains(me);
        int cooldown = (int) (ElectionService.cooldownLeft(country) / 1000);
        Election e = country.election;
        if (e == null) return new ElectionInfo("", "", 0, List.of(), "", false, petition, needed, signed, cooldown);
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, String> c : e.candidates.entrySet()) {
            candidates.add(new Candidate(c.getValue(), e.votesFor(c.getKey())));
        }
        UUID vote = e.votes.get(me);
        String myVote = vote == null ? "" : e.candidates.getOrDefault(vote, "");
        return new ElectionInfo(e.type.name(), e.city, (int) (e.remainingMs() / 1000), candidates, myVote,
                e.candidates.containsKey(me), petition, needed, signed, cooldown);
    }

    private static List<FlagOverride> flagOverrides(Country country) {
        List<FlagOverride> list = new ArrayList<>();
        for (Map.Entry<ProtectionFlag, Boolean> e : country.flags.entrySet()) {
            list.add(new FlagOverride("", e.getKey().key(), e.getValue()));
        }
        for (Map.Entry<String, Map<ProtectionFlag, Boolean>> city : country.cityFlags.entrySet()) {
            for (Map.Entry<ProtectionFlag, Boolean> e : city.getValue().entrySet()) {
                list.add(new FlagOverride(city.getKey(), e.getKey().key(), e.getValue()));
            }
        }
        return list;
    }

    private static int defaultFlagMask() {
        int mask = 0;
        for (ProtectionFlag f : ProtectionFlag.values()) if (f.defaultValue()) mask |= 1 << f.ordinal();
        return mask;
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        if (server != null && server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    // ── Serialization ────────────────────────────────────────────────────

    private static void write(FriendlyByteBuf buf, CountryDetailsPayload p) {
        buf.writeCollection(p.members, (b, m) -> {
            b.writeUtf(m.name);
            b.writeUtf(m.role);
            b.writeBoolean(m.online);
            b.writeVarInt(m.salary);
        });
        buf.writeCollection(p.relations, (b, r) -> {
            b.writeUtf(r.country);
            b.writeUtf(r.status);
        });
        buf.writeUtf(p.pendingFrom);
        buf.writeUtf(p.pendingType);
        buf.writeVarInt(p.warCost);
        buf.writeVarInt(p.secedeThreshold);
        buf.writeVarInt(p.permissions);

        EconomySummary e = p.economy;
        buf.writeInt(e.treasury());
        buf.writeInt(e.maxBalance());
        buf.writeVarInt(e.chunks());
        buf.writeInt(e.directIncome());
        buf.writeInt(e.federalTaxIncome());
        buf.writeInt(e.embassyIncome());
        buf.writeInt(e.upkeep());
        buf.writeUtf(e.tributeTo());
        buf.writeInt(e.tributeAmount());
        buf.writeVarInt(e.vaults());
        buf.writeVarInt(e.embassies());
        buf.writeVarInt(e.activeEmbassies());
        buf.writeVarInt(e.federalTaxRate());
        buf.writeVarInt(e.salaryCost());
        buf.writeVarInt(e.incomeTaxReturn());
        buf.writeVarInt(e.incomeTaxRate());
        buf.writeInt(e.loanPayment());
        buf.writeInt(e.loanIncome());
        buf.writeInt(e.debt());
        buf.writeInt(e.settlementIncome());
        buf.writeInt(e.nonCoreLoss());

        buf.writeCollection(p.flags, (b, f) -> {
            b.writeUtf(f.target());
            b.writeUtf(f.flag());
            b.writeBoolean(f.value());
        });
        buf.writeVarInt(p.flagDefaults);

        buf.writeUtf(p.capital);
        buf.writeCollection(p.cities, (b, c) -> {
            b.writeUtf(c.name);
            b.writeUtf(c.mayor);
            b.writeInt(c.balance);
            b.writeVarInt(c.chunks);
            b.writeVarInt(c.development);
            b.writeVarInt(c.boughtDevelopment);
            b.writeInt(c.nextDevelopmentCost);
            b.writeVarInt(c.nonCore);
            Breakdown.write(b, c.unrest);
            b.writeInt(c.taxPercent);
        });
        buf.writeVarInt(p.laws);
        buf.writeVarInt(p.lawCooldown);

        ElectionInfo el = p.election;
        buf.writeUtf(el.type);
        buf.writeUtf(el.city);
        buf.writeVarInt(el.secondsLeft);
        buf.writeCollection(el.candidates, (b, c) -> {
            b.writeUtf(c.name);
            b.writeVarInt(c.votes);
        });
        buf.writeUtf(el.myVote);
        buf.writeBoolean(el.candidate);
        buf.writeVarInt(el.petition);
        buf.writeVarInt(el.petitionNeeded);
        buf.writeBoolean(el.signed);
        buf.writeVarInt(el.cooldown);

        buf.writeCollection(p.sieges, (b, s) -> {
            b.writeUtf(s.attacker);
            b.writeUtf(s.defender);
            b.writeInt(s.x);
            b.writeInt(s.z);
            b.writeVarInt(s.percent);
            b.writeVarInt(s.secondsLeft);
        });
        buf.writeCollection(p.loans, (b, l) -> {
            b.writeUtf(l.other);
            b.writeInt(l.remaining);
            b.writeInt(l.installment);
            b.writeBoolean(l.owe);
        });
        buf.writeUtf(p.loanOffer.lender);
        buf.writeInt(p.loanOffer.amount);
        buf.writeVarInt(p.loanOffer.interest);
        buf.writeVarInt(p.loanOffer.cycles);
        buf.writeCollection(p.news, (b, n) -> {
            b.writeLong(n.time);
            b.writeUtf(n.key);
            b.writeCollection(n.args, FriendlyByteBuf::writeUtf);
        });
        buf.writeVarInt(p.siegeCost);
        buf.writeVarInt(p.maxLoanInterest);
        buf.writeVarInt(p.maxLoanCycles);
        var lp = p.landPrices;
        buf.writeVarInt(lp.base());
        buf.writeVarInt(lp.growthPercent());
        buf.writeVarInt(lp.growthStart());
        buf.writeVarInt(lp.max());
        buf.writeVarInt(lp.outpostMultiplier());
        buf.writeInt(lp.modifierPercent());
        buf.writeBoolean(p.settlementsEnabled);
        buf.writeVarInt(p.annexReputation);
        buf.writeCollection(p.settlements, (b, s) -> {
            b.writeUtf(s.key);
            b.writeVarInt(s.kind);
            b.writeUtf(s.name);
            b.writeInt(s.chunkX);
            b.writeInt(s.chunkZ);
            b.writeVarInt(s.population);
            b.writeInt(s.mood);
            b.writeUtf(s.owner);
            b.writeVarInt(s.status);
            b.writeVarInt(s.tax);
        });

        RealmInfo r = p.realm;
        buf.writeBoolean(r.enabled);
        buf.writeInt(r.stability);
        buf.writeInt(r.progress);
        buf.writeVarInt(r.perLevel);
        Breakdown.write(buf, r.trend);
        buf.writeVarInt(r.legitimacy10);
        Breakdown.write(buf, r.legitimacyTrend);
        buf.writeInt(r.prestige10);
        buf.writeVarInt(r.overextension);
        buf.writeVarInt(r.stabilizeCost);
        buf.writeVarInt(r.stabilizeCooldown);
        buf.writeVarInt(r.coreCycles);
        buf.writeVarInt(r.maxDevelopment);
        buf.writeVarInt(r.developmentTaxPercent);
        buf.writeVarInt(r.unrestSecede);
        buf.writeVarInt(r.nonCoreTaxPercent);
        buf.writeCollection(r.modifiers, Breakdown::write);

        buf.writeCollection(p.nonCore, (b, v) -> {
            b.writeLong(v.pos);
            b.writeVarInt(v.value);
        });
        buf.writeCollection(p.lostCores, (b, v) -> b.writeLong(v));
        buf.writeCollection(p.chunkIncome, (b, v) -> {
            b.writeLong(v.pos);
            b.writeVarInt(v.value);
        });
        buf.writeCollection(p.ledger, (b, l) -> {
            b.writeUtf(l.country);
            b.writeVarInt(l.chunks);
            b.writeVarInt(l.cities);
            b.writeVarInt(l.citizens);
            b.writeVarInt(l.development);
            b.writeInt(l.stability);
            b.writeInt(l.prestige);
            b.writeVarInt(l.legitimacy);
        });
        buf.writeCollection(p.history, (b, h) -> {
            b.writeUtf(h.country);
            b.writeCollection(h.samples, (bb, s) -> {
                bb.writeVarInt(s.length);
                for (int v : s) bb.writeInt(v);
            });
        });
        buf.writeCollection(p.alerts, (b, a) -> {
            b.writeUtf(a.key());
            b.writeCollection(a.args(), FriendlyByteBuf::writeUtf);
            b.writeVarInt(a.level());
            b.writeVarInt(a.tab());
        });
    }

    private static CountryDetailsPayload read(FriendlyByteBuf buf) {
        List<Member> members = buf.readList(b -> new Member(b.readUtf(), b.readUtf(), b.readBoolean(), b.readVarInt()));
        List<Relation> relations = buf.readList(b -> new Relation(b.readUtf(), b.readUtf()));
        String pendingFrom = buf.readUtf();
        String pendingType = buf.readUtf();
        int warCost = buf.readVarInt();
        int secedeThreshold = buf.readVarInt();
        int permissions = buf.readVarInt();

        EconomySummary economy = new EconomySummary(
                buf.readInt(), buf.readInt(), buf.readVarInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());

        List<FlagOverride> flags = buf.readList(b -> new FlagOverride(b.readUtf(), b.readUtf(), b.readBoolean()));
        int flagDefaults = buf.readVarInt();

        String capital = buf.readUtf();
        List<City> cities = buf.readList(b -> new City(b.readUtf(), b.readUtf(), b.readInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readInt(), b.readVarInt(), Breakdown.read(b), b.readInt()));
        int laws = buf.readVarInt();
        int lawCooldown = buf.readVarInt();

        String elType = buf.readUtf();
        String elCity = buf.readUtf();
        int elLeft = buf.readVarInt();
        List<Candidate> candidates = buf.readList(b -> new Candidate(b.readUtf(), b.readVarInt()));
        ElectionInfo election = new ElectionInfo(elType, elCity, elLeft, candidates, buf.readUtf(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());

        List<SiegeInfo> sieges = buf.readList(b -> new SiegeInfo(b.readUtf(), b.readUtf(), b.readInt(), b.readInt(),
                b.readVarInt(), b.readVarInt()));
        List<LoanInfo> loans = buf.readList(b -> new LoanInfo(b.readUtf(), b.readInt(), b.readInt(), b.readBoolean()));
        LoanOfferInfo offer = new LoanOfferInfo(buf.readUtf(), buf.readInt(), buf.readVarInt(), buf.readVarInt());
        List<News> news = buf.readList(b -> new News(b.readLong(), b.readUtf(), b.readList(FriendlyByteBuf::readUtf)));

        int siegeCost = buf.readVarInt();
        int maxLoanInterest = buf.readVarInt();
        int maxLoanCycles = buf.readVarInt();
        var landPrices = new net.krona.politicsmod.economy.LandPrices.Rules(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readInt());
        boolean settlementsEnabled = buf.readBoolean();
        int annexReputation = buf.readVarInt();
        List<SettlementInfo> settlements = buf.readList(b -> new SettlementInfo(b.readUtf(), b.readVarInt(), b.readUtf(),
                b.readInt(), b.readInt(), b.readVarInt(), b.readInt(), b.readUtf(), b.readVarInt(), b.readVarInt()));

        RealmInfo realm = new RealmInfo(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt(), Breakdown.read(buf),
                buf.readVarInt(), Breakdown.read(buf), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readList(Breakdown::read));
        List<ChunkValue> nonCore = buf.readList(b -> new ChunkValue(b.readLong(), b.readVarInt()));
        List<Long> lostCores = buf.readList(FriendlyByteBuf::readLong);
        List<ChunkValue> chunkIncome = buf.readList(b -> new ChunkValue(b.readLong(), b.readVarInt()));
        List<LedgerRow> ledger = buf.readList(b -> new LedgerRow(b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt()));
        List<History> history = buf.readList(b -> new History(b.readUtf(), b.readList(bb -> {
            int[] s = new int[bb.readVarInt()];
            for (int i = 0; i < s.length; i++) s[i] = bb.readInt();
            return s;
        })));
        List<net.krona.politicsmod.realm.Alerts.Alert> alerts = buf.readList(b -> new net.krona.politicsmod.realm.Alerts.Alert(
                b.readUtf(), b.readList(FriendlyByteBuf::readUtf), b.readVarInt(), b.readVarInt()));

        return new CountryDetailsPayload(members, relations, pendingFrom, pendingType, warCost, secedeThreshold, permissions,
                economy, flags, flagDefaults, capital, cities, laws, lawCooldown, election, sieges, loans, offer, news,
                siegeCost, maxLoanInterest, maxLoanCycles, landPrices, settlementsEnabled, annexReputation, settlements,
                realm, nonCore, lostCores, chunkIncome, ledger, history, alerts);
    }
}

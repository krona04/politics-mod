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
        int maxLoanCycles
) implements CustomPacketPayload {

    /** A protection flag set explicitly; target "" = the whole country, otherwise a city name. */
    public record FlagOverride(String target, String flag, boolean value) {
    }

    public record Member(String name, String role, boolean online, int salary) {
    }

    public record Relation(String country, String status) {
    }

    public record City(String name, String mayor, int balance, int chunks) {
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

        Map<String, Integer> cityChunks = new HashMap<>();
        for (ChunkPos pos : mgr.getChunksOf(countryName)) {
            String city = mgr.getCityAt(pos);
            if (city != null) cityChunks.merge(city, 1, Integer::sum);
        }
        List<City> cities = new ArrayList<>();
        for (String city : country.cities) {
            UUID mayor = country.cityMayors.get(city);
            cities.add(new City(city, mayor == null ? "" : nameOf(server, mayor),
                    country.cityBalances.getOrDefault(city, 0), cityChunks.getOrDefault(city, 0)));
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
        return new CountryDetailsPayload(
                members,
                relations,
                pending != null ? pending[0] : "",
                pending != null ? pending[1] : "",
                cfg.warCost,
                cfg.secedeThresholdPercent,
                RoleAction.mask(country.getRole(me)),
                EconomySummary.of(mgr, countryName),
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
                cfg.maxLoanCycles);
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
                buf.readInt(), buf.readInt(), buf.readInt());

        List<FlagOverride> flags = buf.readList(b -> new FlagOverride(b.readUtf(), b.readUtf(), b.readBoolean()));
        int flagDefaults = buf.readVarInt();

        String capital = buf.readUtf();
        List<City> cities = buf.readList(b -> new City(b.readUtf(), b.readUtf(), b.readInt(), b.readVarInt()));
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

        return new CountryDetailsPayload(members, relations, pendingFrom, pendingType, warCost, secedeThreshold, permissions,
                economy, flags, flagDefaults, capital, cities, laws, lawCooldown, election, sieges, loans, offer, news,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}

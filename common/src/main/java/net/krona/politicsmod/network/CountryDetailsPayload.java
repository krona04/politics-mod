package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.ModCommands;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.EconomySummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data for the Citizens, Diplomacy and Economy tabs (server -> client).
 * Sent when the menu opens and on RequestCountryDetailsPayload after GUI actions.
 */
public record CountryDetailsPayload(
        List<Member> members,
        List<Relation> relations,
        String pendingFrom,     // "" when there is no incoming proposal
        String pendingType,     // "ALLIANCE" | "NEUTRAL"
        int warCost,
        int secedeThreshold,
        EconomySummary economy
) implements CustomPacketPayload {

    public record Member(String name, String role, boolean online) {
    }

    public record Relation(String country, String status) {
    }

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

    // ── Server-side build ────────────────────────────────────────────────

    /** Returns null if the player is not in a country. */
    public static CountryDetailsPayload build(ServerPlayer player) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return null;
        String countryName = mgr.getPlayerCountry(player.getUUID());
        if (countryName == null) return null;
        Country country = mgr.getCountry(countryName);
        MinecraftServer server = player.getServer();

        List<Member> members = new ArrayList<>();
        for (Map.Entry<UUID, CountryRole> e : country.getMembers().entrySet()) {
            ServerPlayer online = server != null ? server.getPlayerList().getPlayer(e.getKey()) : null;
            String name = online != null ? online.getGameProfile().getName() : nameOf(server, e.getKey());
            members.add(new Member(name, e.getValue().name(), online != null));
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

        PoliticsConfig cfg = PoliticsConfig.get();
        return new CountryDetailsPayload(
                members,
                relations,
                pending != null ? pending[0] : "",
                pending != null ? pending[1] : "",
                cfg.warCost,
                cfg.secedeThresholdPercent,
                EconomySummary.of(mgr, countryName));
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
        buf.writeVarInt(p.members.size());
        for (Member m : p.members) {
            buf.writeUtf(m.name);
            buf.writeUtf(m.role);
            buf.writeBoolean(m.online);
        }
        buf.writeVarInt(p.relations.size());
        for (Relation r : p.relations) {
            buf.writeUtf(r.country);
            buf.writeUtf(r.status);
        }
        buf.writeUtf(p.pendingFrom);
        buf.writeUtf(p.pendingType);
        buf.writeVarInt(p.warCost);
        buf.writeVarInt(p.secedeThreshold);

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
    }

    private static CountryDetailsPayload read(FriendlyByteBuf buf) {
        int memberCount = buf.readVarInt();
        List<Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new Member(buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        int relationCount = buf.readVarInt();
        List<Relation> relations = new ArrayList<>(relationCount);
        for (int i = 0; i < relationCount; i++) {
            relations.add(new Relation(buf.readUtf(), buf.readUtf()));
        }
        String pendingFrom = buf.readUtf();
        String pendingType = buf.readUtf();
        int warCost = buf.readVarInt();
        int secedeThreshold = buf.readVarInt();

        EconomySummary economy = new EconomySummary(
                buf.readInt(), buf.readInt(), buf.readVarInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());

        return new CountryDetailsPayload(members, relations, pendingFrom, pendingType, warCost, secedeThreshold, economy);
    }
}

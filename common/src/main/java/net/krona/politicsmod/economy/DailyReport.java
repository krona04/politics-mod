package net.krona.politicsmod.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The economy runs in short cycles; instead of a chat message after each of them, every country gets one report
 * at dawn with the totals of the past day, and every player one line about their salary and rent.
 *
 * The totals live only in memory: after a restart the first report covers the cycles since the restart.
 */
public final class DailyReport {

    private static final int DAY_TICKS = 24_000;

    /** Money that went in or out of a national treasury during the day, by source. Keys are the Economy tab lines. */
    private static final class CountryTally {
        final Map<String, Long> income = new LinkedHashMap<>();
        final Map<String, Long> expenses = new LinkedHashMap<>();
        long capped;
        int unpaidSalaries;
        long unpaidTotal;
    }

    private static final class PlayerTally {
        long salaryGross, salaryTax, rent;
        int unpaid;
        String unpaidBy = "";
    }

    private static final Map<String, CountryTally> COUNTRIES = new HashMap<>();
    private static final Map<UUID, PlayerTally> PLAYERS = new HashMap<>();
    private static int cycles;
    private static long lastDay = -1;

    private DailyReport() {
    }

    /** Forget everything when the server stops: in singleplayer the next world runs in the same JVM. */
    public static void reset() {
        COUNTRIES.clear();
        PLAYERS.clear();
        cycles = 0;
        lastDay = -1;
    }

    // ── Recording, called from the economy cycle ─────────────────────────

    public static void cycle() {
        cycles++;
    }

    public static void income(Country country, String line, long amount) {
        if (amount > 0) tally(country).income.merge(line, amount, Long::sum);
    }

    public static void expense(Country country, String line, long amount) {
        if (amount > 0) tally(country).expenses.merge(line, amount, Long::sum);
    }

    /** Money the treasury could not keep because it was full. */
    public static void capped(Country country, long amount) {
        if (amount > 0) tally(country).capped += amount;
    }

    public static void salaryPaid(UUID player, int gross, int tax) {
        PlayerTally t = PLAYERS.computeIfAbsent(player, id -> new PlayerTally());
        t.salaryGross += gross;
        t.salaryTax += tax;
    }

    public static void salaryUnpaid(Country country, UUID player, int cost) {
        PlayerTally t = PLAYERS.computeIfAbsent(player, id -> new PlayerTally());
        t.unpaid++;
        t.unpaidBy = country.getName();
        CountryTally c = tally(country);
        c.unpaidSalaries++;
        c.unpaidTotal += cost;
    }

    public static void rentPaid(UUID player, int amount) {
        PLAYERS.computeIfAbsent(player, id -> new PlayerTally()).rent += amount;
    }

    private static CountryTally tally(Country country) {
        return COUNTRIES.computeIfAbsent(country.getName(), n -> new CountryTally());
    }

    // ── Sending, checked every tick ──────────────────────────────────────

    /** Sends the reports when a new day starts (at dawn, or right after the night is slept through). */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        // Without the daylight cycle the sun never rises, so whole days of play time count instead
        long time = overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? overworld.getDayTime() : overworld.getGameTime();
        long day = time / DAY_TICKS;
        if (lastDay < 0) {
            lastDay = day;
            return;
        }
        if (day == lastDay) return;
        lastDay = day;
        if (cycles > 0 && PoliticsConfig.get().economyDailyReport) send(server, day + 1);
        COUNTRIES.clear();
        PLAYERS.clear();
        cycles = 0;
    }

    private static void send(MinecraftServer server, long day) {
        PoliticsManager mgr = PoliticsManager.get(server.overworld());
        if (mgr == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String countryName = mgr.getPlayerCountry(player.getUUID());
            Country country = countryName == null ? null : mgr.getCountry(countryName);
            if (country != null && country.getRole(player.getUUID()) != CountryRole.GUEST) {
                sendCountry(player, country, COUNTRIES.get(countryName), day);
            }
            sendPersonal(player, PLAYERS.get(player.getUUID()));
        }
    }

    private static void sendCountry(ServerPlayer player, Country country, CountryTally t, long day) {
        if (t == null) t = new CountryTally();
        long in = t.income.values().stream().mapToLong(Long::longValue).sum();
        long out = t.expenses.values().stream().mapToLong(Long::longValue).sum();
        long net = in - out;

        player.sendSystemMessage(Component.translatable("message.politicsmod.report.title", day, country.getName(), cycles)
                .withStyle(ChatFormatting.GOLD));

        MutableComponent details = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Long> e : t.income.entrySet()) {
            details.append(first ? "" : "\n").append(line(e.getKey(), "+" + e.getValue() + "$", ChatFormatting.GREEN));
            first = false;
        }
        for (Map.Entry<String, Long> e : t.expenses.entrySet()) {
            details.append(first ? "" : "\n").append(line(e.getKey(), "−" + e.getValue() + "$", ChatFormatting.RED));
            first = false;
        }
        MutableComponent totals = Component.translatable("message.politicsmod.report.totals",
                money("+", in, ChatFormatting.GREEN), money("−", out, ChatFormatting.RED),
                net >= 0 ? money("+", net, ChatFormatting.GREEN) : money("−", -net, ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY);
        if (!first) {
            totals.append(Component.literal(" ⓘ").withStyle(ChatFormatting.GRAY))
                    .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details)));
        }
        player.sendSystemMessage(Component.literal(" ").append(totals));
        player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.treasury", country.balance)
                .withStyle(country.balance < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW)));

        if (t.capped > 0) {
            player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.capped", t.capped)
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (t.unpaidSalaries > 0 && country.getRole(player.getUUID()) == CountryRole.LEADER) {
            player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.unpaid_salaries",
                    t.unpaidSalaries, t.unpaidTotal).withStyle(ChatFormatting.RED)));
        }
    }

    private static void sendPersonal(ServerPlayer player, PlayerTally t) {
        if (t == null) return;
        if (t.salaryGross > 0) {
            player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.salary",
                    t.salaryGross - t.salaryTax, t.salaryTax).withStyle(ChatFormatting.GREEN)));
        }
        if (t.unpaid > 0) {
            player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.salary_unpaid",
                    t.unpaidBy, t.unpaid).withStyle(ChatFormatting.RED)));
        }
        if (t.rent > 0) {
            player.sendSystemMessage(Component.literal(" ").append(Component.translatable("message.politicsmod.report.rent", t.rent)
                    .withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static MutableComponent money(String sign, long amount, ChatFormatting color) {
        return Component.literal(sign + amount + "$").withStyle(color);
    }

    private static MutableComponent line(String key, String amount, ChatFormatting color) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(": ")
                .append(Component.literal(amount).withStyle(color));
    }
}

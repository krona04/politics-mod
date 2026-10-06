package net.krona.politicsmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.platform.Permissions;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Election;
import net.krona.politicsmod.politics.ElectionService;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.Loan;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.Notify;
import net.krona.politicsmod.war.SiegeService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Elections, laws, sieges, loans and the news feed. */
public final class StateCommands {

    /** A loan offer waiting for the borrower's answer. */
    public record LoanOffer(String lender, int amount, int interest, int cycles) {
    }

    /** Borrower -> the latest offer. Not saved: offers expire with a restart. */
    private static final Map<String, LoanOffer> LOAN_OFFERS = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> LAWS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Law.values()).map(Law::key), builder);

    private StateCommands() {
    }

    public static LoanOffer getLoanOffer(String borrower) {
        return LOAN_OFFERS.get(borrower);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("election")
                .requires(src -> Permissions.command(src, "election"))
                .executes(StateCommands::electionStatus)
                .then(Commands.literal("status").executes(StateCommands::electionStatus))
                .then(Commands.literal("start")
                    .then(Commands.literal("leader").executes(ctx -> startElection(ctx, Election.Type.LEADER, "")))
                    .then(Commands.literal("mayor")
                        .then(Commands.argument("city", StringArgumentType.greedyString())
                            .executes(ctx -> startElection(ctx, Election.Type.MAYOR, StringArgumentType.getString(ctx, "city"))))))
                .then(Commands.literal("run").executes(StateCommands::runForOffice))
                .then(Commands.literal("vote")
                    .then(Commands.argument("candidate", StringArgumentType.word())
                        .executes(ctx -> vote(ctx, StringArgumentType.getString(ctx, "candidate")))))
                .then(Commands.literal("petition").executes(StateCommands::signPetition)))

            .then(Commands.literal("law")
                .requires(src -> Permissions.command(src, "law"))
                .executes(StateCommands::listLaws)
                .then(Commands.literal("enact")
                    .then(Commands.argument("law", StringArgumentType.word()).suggests(LAWS)
                        .executes(ctx -> changeLaw(ctx, StringArgumentType.getString(ctx, "law"), true))))
                .then(Commands.literal("repeal")
                    .then(Commands.argument("law", StringArgumentType.word()).suggests(LAWS)
                        .executes(ctx -> changeLaw(ctx, StringArgumentType.getString(ctx, "law"), false)))))

            .then(Commands.literal("siege")
                .requires(src -> Permissions.command(src, "siege"))
                .executes(StateCommands::siegeStatus)
                .then(Commands.literal("start").executes(StateCommands::startSiege))
                .then(Commands.literal("status").executes(StateCommands::siegeStatus))
                .then(Commands.literal("cancel").executes(StateCommands::cancelSiege)))

            .then(Commands.literal("loan")
                .requires(src -> Permissions.command(src, "loan"))
                .executes(StateCommands::listLoans)
                .then(Commands.literal("offer")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("interest", IntegerArgumentType.integer(0))
                            .then(Commands.argument("cycles", IntegerArgumentType.integer(1))
                                .then(Commands.argument("country", StringArgumentType.greedyString())
                                    .executes(ctx -> offerLoan(ctx,
                                            StringArgumentType.getString(ctx, "country"),
                                            IntegerArgumentType.getInteger(ctx, "amount"),
                                            IntegerArgumentType.getInteger(ctx, "interest"),
                                            IntegerArgumentType.getInteger(ctx, "cycles"))))))))
                .then(Commands.literal("accept").executes(ctx -> answerLoan(ctx, true)))
                .then(Commands.literal("reject").executes(ctx -> answerLoan(ctx, false)))
                .then(Commands.literal("list").executes(StateCommands::listLoans)))

            .then(Commands.literal("news")
                .requires(src -> Permissions.command(src, "news"))
                .executes(ctx -> showNews(ctx, 10))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                    .executes(ctx -> showNews(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void fail(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    private static void ok(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GREEN));
    }

    /** The player's country, or null after telling them they have none. */
    private static Country countryOf(ServerPlayer p, PoliticsManager mgr) {
        String name = mgr.getPlayerCountry(p.getUUID());
        if (name == null) {
            fail(p, "message.politicsmod.no_state");
            return null;
        }
        return mgr.getCountry(name);
    }

    private static Country leaderCountry(ServerPlayer p, PoliticsManager mgr) {
        Country c = countryOf(p, mgr);
        if (c == null) return null;
        if (c.getRole(p.getUUID()) != CountryRole.LEADER) {
            fail(p, "message.politicsmod.diplomacy.leader_only");
            return null;
        }
        return c;
    }

    private static String minutes(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ── Elections ────────────────────────────────────────────────────────

    private static int electionStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        Election e = c.election;
        if (e == null) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.election.none").withStyle(ChatFormatting.GRAY));
            long cd = ElectionService.cooldownLeft(c);
            if (cd > 0) p.sendSystemMessage(Component.translatable("message.politicsmod.election.cooldown", minutes(cd)).withStyle(ChatFormatting.GRAY));
        } else {
            Component office = e.type == Election.Type.LEADER
                    ? Component.translatable("gui.politicsmod.politics.office_leader")
                    : Component.translatable("gui.politicsmod.politics.office_mayor", e.city);
            p.sendSystemMessage(Component.translatable("message.politicsmod.election.status", office, minutes(e.remainingMs()))
                    .withStyle(ChatFormatting.GOLD));
            if (e.candidates.isEmpty()) {
                p.sendSystemMessage(Component.translatable("message.politicsmod.election.no_candidates_yet").withStyle(ChatFormatting.GRAY));
            }
            for (Map.Entry<UUID, String> cand : e.candidates.entrySet()) {
                boolean mine = cand.getKey().equals(e.votes.get(p.getUUID()));
                p.sendSystemMessage(Component.literal((mine ? " ✔ " : " • ") + cand.getValue() + " — " + e.votesFor(cand.getKey()))
                        .withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            }
        }
        p.sendSystemMessage(Component.translatable("message.politicsmod.election.petition_status",
                c.petition.size(), ElectionService.petitionNeeded(c)).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int startElection(CommandContext<CommandSourceStack> ctx, Election.Type type, String city) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : leaderCountry(p, mgr);
        if (c == null) return 0;
        if (c.election != null) { fail(p, "message.politicsmod.election.already_running"); return 0; }
        long cd = ElectionService.cooldownLeft(c);
        if (cd > 0) { fail(p, "message.politicsmod.election.cooldown", minutes(cd)); return 0; }
        if (type == Election.Type.MAYOR && !c.cities.contains(city)) { fail(p, "message.politicsmod.city.not_found", city); return 0; }
        ElectionService.start(mgr, c, type, city);
        return 1;
    }

    private static int runForOffice(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        if (c.election == null) { fail(p, "message.politicsmod.election.none"); return 0; }
        if (c.election.candidates.containsKey(p.getUUID())) { fail(p, "message.politicsmod.election.already_candidate"); return 0; }
        c.election.candidates.put(p.getUUID(), p.getGameProfile().getName());
        mgr.setDirty();
        Notify.members(c, Component.translatable("message.politicsmod.election.new_candidate", p.getGameProfile().getName())
                .withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int vote(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        Election e = c.election;
        if (e == null) { fail(p, "message.politicsmod.election.none"); return 0; }
        UUID candidate = null;
        for (Map.Entry<UUID, String> cand : e.candidates.entrySet()) {
            if (cand.getValue().equalsIgnoreCase(name)) candidate = cand.getKey();
        }
        if (candidate == null) { fail(p, "message.politicsmod.election.not_candidate", name); return 0; }
        e.votes.put(p.getUUID(), candidate);
        mgr.setDirty();
        ok(p, "message.politicsmod.election.voted", e.candidates.get(candidate));
        return 1;
    }

    private static int signPetition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        if (c.getRole(p.getUUID()) == CountryRole.LEADER) { fail(p, "message.politicsmod.election.leader_petition"); return 0; }
        if (c.election != null) { fail(p, "message.politicsmod.election.already_running"); return 0; }
        if (!c.petition.add(p.getUUID())) { fail(p, "message.politicsmod.election.already_signed"); return 0; }
        mgr.setDirty();
        int needed = ElectionService.petitionNeeded(c);
        Notify.members(c, Component.translatable("message.politicsmod.election.signed", p.getGameProfile().getName(),
                c.petition.size(), needed).withStyle(ChatFormatting.YELLOW));
        if (c.petition.size() >= needed && ElectionService.cooldownLeft(c) > 0) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.election.petition_waits",
                    minutes(ElectionService.cooldownLeft(c))).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    // ── Laws ─────────────────────────────────────────────────────────────

    private static int listLaws(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        p.sendSystemMessage(Component.translatable("message.politicsmod.law.header", c.getName()).withStyle(ChatFormatting.GOLD));
        for (Law law : Law.values()) {
            boolean on = c.hasLaw(law);
            p.sendSystemMessage(Component.literal(on ? " ✔ " : " ✘ ")
                    .append(Component.translatable(law.nameKey()))
                    .append(Component.literal(" (" + law.key() + ") — "))
                    .append(Component.translatable("law.politicsmod." + law.key() + ".desc"))
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int changeLaw(CommandContext<CommandSourceStack> ctx, String key, boolean enact) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : leaderCountry(p, mgr);
        if (c == null) return 0;
        Law law = Law.byKey(key);
        if (law == null) { fail(p, "message.politicsmod.law.unknown", key); return 0; }
        if (c.hasLaw(law) == enact) { fail(p, enact ? "message.politicsmod.law.already_on" : "message.politicsmod.law.already_off"); return 0; }
        long cd = c.lawChangedAt + PoliticsConfig.get().lawCooldownMs() - System.currentTimeMillis();
        if (cd > 0) { fail(p, "message.politicsmod.law.cooldown", minutes(cd)); return 0; }
        if (enact) c.laws.add(law); else c.laws.remove(law);
        c.lawChangedAt = System.currentTimeMillis();
        mgr.setDirty();
        mgr.postNews(enact ? "news.politicsmod.law_enacted" : "news.politicsmod.law_repealed", List.of(c.getName()),
                c.getName(), NewsEntry.TRANSLATE_PREFIX + law.nameKey());
        Notify.members(c, Component.translatable(enact ? "message.politicsmod.law.enacted" : "message.politicsmod.law.repealed",
                Component.translatable(law.nameKey())).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    // ── Sieges ───────────────────────────────────────────────────────────

    private static int startSiege(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        if (mgr == null) return 0;
        Component error = SiegeService.start(p, mgr);
        if (error != null) {
            p.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int siegeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        List<SiegeService.Siege> sieges = SiegeService.of(c.getName());
        if (sieges.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.siege.none").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (SiegeService.Siege s : sieges) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.siege.entry", s.attacker, s.defender,
                    s.pos.x, s.pos.z, s.percent(), s.secondsLeft()).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int cancelSiege(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : leaderCountry(p, mgr);
        if (c == null) return 0;
        int n = SiegeService.cancel(c.getName());
        if (n == 0) { fail(p, "message.politicsmod.siege.none"); return 0; }
        ok(p, "message.politicsmod.siege.cancelled", n);
        return 1;
    }

    // ── Loans ────────────────────────────────────────────────────────────

    private static int offerLoan(CommandContext<CommandSourceStack> ctx, String target, int amount, int interest, int cycles)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country lender = mgr == null ? null : leaderCountry(p, mgr);
        if (lender == null) return 0;
        Country borrower = mgr.getCountry(target);
        if (borrower == null) { fail(p, "message.politicsmod.country_not_found", target); return 0; }
        if (borrower == lender) { fail(p, "message.politicsmod.cant_manage_own_country"); return 0; }
        if (!mgr.isAllied(lender.getName(), target)) { fail(p, "message.politicsmod.loan.allies_only"); return 0; }
        PoliticsConfig cfg = PoliticsConfig.get();
        if (interest > cfg.maxLoanInterestPercent) { fail(p, "message.politicsmod.loan.interest_too_high", cfg.maxLoanInterestPercent); return 0; }
        if (cycles > cfg.maxLoanCycles) { fail(p, "message.politicsmod.loan.too_long", cfg.maxLoanCycles); return 0; }
        if (lender.balance < amount) { fail(p, "message.politicsmod.loan.no_funds", amount); return 0; }

        LOAN_OFFERS.put(target, new LoanOffer(lender.getName(), amount, interest, cycles));
        Loan preview = Loan.create(lender.getName(), target, amount, interest, cycles);
        Notify.leader(borrower, Component.translatable("message.politicsmod.loan.offer_received", lender.getName(), amount,
                interest, cycles, preview.remaining, preview.installment).withStyle(ChatFormatting.GOLD));
        ok(p, "message.politicsmod.loan.offer_sent", target);
        return 1;
    }

    private static int answerLoan(CommandContext<CommandSourceStack> ctx, boolean accept) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country borrower = mgr == null ? null : leaderCountry(p, mgr);
        if (borrower == null) return 0;
        LoanOffer offer = LOAN_OFFERS.remove(borrower.getName());
        if (offer == null) { fail(p, "message.politicsmod.loan.no_offer"); return 0; }
        Country lender = mgr.getCountry(offer.lender());
        if (lender == null) { fail(p, "message.politicsmod.country_not_found", offer.lender()); return 0; }
        if (!accept) {
            Notify.leader(lender, Component.translatable("message.politicsmod.loan.rejected", borrower.getName()).withStyle(ChatFormatting.RED));
            ok(p, "message.politicsmod.diplomacy.rejected");
            return 1;
        }
        // The situation may have changed since the offer
        if (!mgr.isAllied(lender.getName(), borrower.getName())) { fail(p, "message.politicsmod.loan.allies_only"); return 0; }
        if (lender.balance < offer.amount()) { fail(p, "message.politicsmod.loan.lender_no_funds", lender.getName()); return 0; }

        lender.balance -= offer.amount();
        borrower.balance += offer.amount();
        Loan loan = Loan.create(lender.getName(), borrower.getName(), offer.amount(), offer.interest(), offer.cycles());
        mgr.addLoan(loan);
        mgr.postNews("news.politicsmod.loan_given", List.of(lender.getName(), borrower.getName()),
                lender.getName(), borrower.getName(), String.valueOf(offer.amount()));
        Component msg = Component.translatable("message.politicsmod.loan.accepted", lender.getName(), borrower.getName(),
                offer.amount(), loan.remaining, loan.installment).withStyle(ChatFormatting.GREEN);
        Notify.leader(lender, msg);
        p.sendSystemMessage(msg);
        return 1;
    }

    private static int listLoans(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : countryOf(p, mgr);
        if (c == null) return 0;
        String mine = c.getName();
        p.sendSystemMessage(Component.translatable("message.politicsmod.loan.header", mine, c.balance).withStyle(ChatFormatting.GOLD));
        boolean any = false;
        for (Loan l : mgr.getLoans()) {
            if (l.borrower.equals(mine)) {
                p.sendSystemMessage(Component.translatable("message.politicsmod.loan.owe", l.lender, l.remaining, l.installment).withStyle(ChatFormatting.RED));
                any = true;
            } else if (l.lender.equals(mine)) {
                p.sendSystemMessage(Component.translatable("message.politicsmod.loan.owed", l.borrower, l.remaining, l.installment).withStyle(ChatFormatting.GREEN));
                any = true;
            }
        }
        if (!any) p.sendSystemMessage(Component.translatable("message.politicsmod.loan.none").withStyle(ChatFormatting.GRAY));
        LoanOffer offer = LOAN_OFFERS.get(mine);
        if (offer != null) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.loan.pending", offer.lender(), offer.amount(),
                    offer.interest(), offer.cycles()).withStyle(ChatFormatting.YELLOW));
        }
        if (c.isBankrupt()) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.debt.bankrupt_status").withStyle(ChatFormatting.DARK_RED));
        }
        return 1;
    }

    // ── News ─────────────────────────────────────────────────────────────

    private static int showNews(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack src = ctx.getSource();
        PoliticsManager mgr = PoliticsManager.get(src.getServer().overworld());
        if (mgr == null) return 0;
        List<NewsEntry> news = mgr.getNews();
        src.sendSuccess(() -> Component.translatable("message.politicsmod.news.header").withStyle(ChatFormatting.GOLD), false);
        if (news.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("message.politicsmod.news.empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        for (int i = Math.max(0, news.size() - count); i < news.size(); i++) {
            NewsEntry n = news.get(i);
            src.sendSuccess(() -> Component.literal(" • ").append(n.text()).withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    /** Clears offers that point at a country that no longer exists. */
    public static void forgetCountry(String name) {
        LOAN_OFFERS.remove(name);
        LOAN_OFFERS.values().removeIf(o -> o.lender().equals(name));
    }
}

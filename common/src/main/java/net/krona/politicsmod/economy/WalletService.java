package net.krona.politicsmod.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every change to a player's wallet goes through here. All amounts are validated on the
 * server; balances never go below zero or above walletMaxBalance.
 */
public final class WalletService {

    public enum Result { OK, INVALID_AMOUNT, NO_FUNDS, TARGET_FULL, SELF }

    private WalletService() {
    }

    public static int balance(PoliticsManager mgr, UUID player) {
        return mgr.getWallet(player).balance();
    }

    public static Result transfer(PoliticsManager mgr, UUID from, String fromName, UUID to, String toName, int amount) {
        if (from.equals(to)) return Result.SELF;
        if (amount <= 0) return Result.INVALID_AMOUNT;
        Wallet source = mgr.getWallet(from);
        Wallet target = mgr.getWallet(to);
        if (source.balance() < amount) return Result.NO_FUNDS;
        if (room(target) < amount) return Result.TARGET_FULL;

        int limit = PoliticsConfig.get().walletHistorySize;
        source.apply(-amount, WalletTransaction.TYPE_TRANSFER_OUT, toName, limit);
        target.apply(amount, WalletTransaction.TYPE_TRANSFER_IN, fromName, limit);
        mgr.setDirty();
        return Result.OK;
    }

    public static Result donate(PoliticsManager mgr, UUID player, Country country, int amount) {
        if (amount <= 0) return Result.INVALID_AMOUNT;
        Wallet wallet = mgr.getWallet(player);
        if (wallet.balance() < amount) return Result.NO_FUNDS;

        wallet.apply(-amount, WalletTransaction.TYPE_DONATION, country.getName(), PoliticsConfig.get().walletHistorySize);
        country.balance += amount;
        mgr.setDirty();
        return Result.OK;
    }

    /** Operator command: add (positive) or remove (negative) money. Clamped to the wallet limits. */
    public static int adminAdjust(PoliticsManager mgr, UUID player, int delta) {
        Wallet wallet = mgr.getWallet(player);
        long target = Math.max(0L, Math.min((long) wallet.balance() + delta, PoliticsConfig.get().walletMaxBalance));
        int applied = (int) (target - wallet.balance());
        if (applied != 0) {
            wallet.apply(applied, WalletTransaction.TYPE_ADMIN, "", PoliticsConfig.get().walletHistorySize);
            mgr.setDirty();
        }
        return wallet.balance();
    }

    // ── Market ───────────────────────────────────────────────────────────

    /** Takes money from a buyer's wallet. The caller has already checked the balance. */
    public static void pay(PoliticsManager mgr, UUID player, int amount, String party) {
        mgr.getWallet(player).apply(-amount, WalletTransaction.TYPE_PURCHASE, party, PoliticsConfig.get().walletHistorySize);
        mgr.setDirty();
    }

    /**
     * Pays a seller for a personal listing. Income tax goes to the seller's current country.
     * Whatever doesn't fit into the wallet is lost to the market, so the buyer is never refunded twice.
     */
    public static void creditSale(PoliticsManager mgr, UUID seller, int proceeds, String buyerName) {
        int limit = PoliticsConfig.get().walletHistorySize;
        Wallet wallet = mgr.getWallet(seller);
        int gross = Math.min(proceeds, room(wallet));
        if (gross <= 0) return;

        String countryName = mgr.getPlayerCountry(seller);
        net.krona.politicsmod.politics.Country country = countryName != null ? mgr.getCountry(countryName) : null;
        int tax = country != null ? incomeTax(country, gross) : 0;

        wallet.apply(gross, WalletTransaction.TYPE_SALE, buyerName, limit);
        if (tax > 0) {
            wallet.apply(-tax, WalletTransaction.TYPE_INCOME_TAX, countryName, limit);
            country.balance += tax;
        }
        mgr.setDirty();
    }

    // ── Currency exchange ────────────────────────────────────────────────

    /** The configured exchange item, or null if the exchange is off or misconfigured. */
    public static Item exchangeItem() {
        PoliticsConfig cfg = PoliticsConfig.get();
        if (!cfg.exchangeEnabled) return null;
        ResourceLocation id = ResourceLocation.tryParse(cfg.exchangeItem);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    /**
     * Converts up to {@code count} exchange items from the stack into wallet money.
     * Only as many items as fit into the wallet are taken. Returns the number of items taken.
     */
    public static int exchangeIn(PoliticsManager mgr, UUID player, ItemStack stack, int count) {
        Item currency = exchangeItem();
        if (currency == null || !stack.is(currency) || count <= 0) return 0;
        int rate = PoliticsConfig.get().exchangeRate;
        Wallet wallet = mgr.getWallet(player);
        int taken = Math.min(Math.min(count, stack.getCount()), room(wallet) / rate);
        if (taken <= 0) return 0;

        stack.shrink(taken);
        wallet.apply(taken * rate, WalletTransaction.TYPE_EXCHANGE_IN, "", PoliticsConfig.get().walletHistorySize);
        mgr.setDirty();
        return taken;
    }

    /** Converts wallet money back into items. Returns the item stack to hand out (empty on failure). */
    public static ItemStack exchangeOut(PoliticsManager mgr, UUID player, int count) {
        Item currency = exchangeItem();
        PoliticsConfig cfg = PoliticsConfig.get();
        if (currency == null || !cfg.exchangeWithdrawEnabled || count <= 0) return ItemStack.EMPTY;
        long cost = (long) count * cfg.exchangeRate;
        Wallet wallet = mgr.getWallet(player);
        if (cost > wallet.balance()) return ItemStack.EMPTY;

        wallet.apply((int) -cost, WalletTransaction.TYPE_EXCHANGE_OUT, "", cfg.walletHistorySize);
        mgr.setDirty();
        return new ItemStack(currency, count);
    }

    // ── Salaries ─────────────────────────────────────────────────────────

    /** Income tax withheld from a gross amount at the country's current rate. */
    public static int incomeTax(Country country, int gross) {
        return gross * Math.max(0, country.incomeTaxRate) / 100;
    }

    /**
     * Pays every salary of the country from its treasury. The treasury pays the gross salary and
     * immediately gets the income tax back, so it effectively spends gross - tax.
     * Salaries that the treasury can't cover are skipped this cycle.
     */
    public static void paySalaries(PoliticsManager mgr, Country country, MinecraftServer server) {
        if (country.salaries.isEmpty()) return;

        List<Map.Entry<UUID, Integer>> order = new ArrayList<>(country.salaries.entrySet());
        order.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(e -> -country.getRole(e.getKey()).getLevel())
                .thenComparing(e -> e.getKey().toString()));

        int historyLimit = PoliticsConfig.get().walletHistorySize;

        for (Map.Entry<UUID, Integer> e : order) {
            UUID playerId = e.getKey();
            Wallet wallet = mgr.getWallet(playerId);
            int gross = Math.min(e.getValue(), room(wallet));
            if (gross <= 0) continue;

            int tax = incomeTax(country, gross);
            int cost = gross - tax;
            if (country.balance < cost) {
                DailyReport.salaryUnpaid(country, playerId, cost);
                continue;
            }

            country.balance -= cost;
            wallet.apply(gross, WalletTransaction.TYPE_SALARY, country.getName(), historyLimit);
            if (tax > 0) wallet.apply(-tax, WalletTransaction.TYPE_INCOME_TAX, country.getName(), historyLimit);
            // Salaries and the income tax that comes back, shown as two lines like on the Economy tab
            DailyReport.expense(country, "gui.politicsmod.economy.line_salaries", gross);
            DailyReport.income(country, "gui.politicsmod.economy.line_income_tax", tax);
            DailyReport.salaryPaid(playerId, gross, tax);
        }
        mgr.setDirty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static int room(Wallet wallet) {
        return Math.max(0, PoliticsConfig.get().walletMaxBalance - wallet.balance());
    }
}

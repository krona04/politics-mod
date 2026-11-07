package net.krona.politicsmod.politics;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.economy.WalletService;
import net.krona.politicsmod.realm.Realm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Forecast of a country's treasury for one economy cycle.
 *
 * The formulas mirror PoliticsManager.processEconomyCycle; update this when the cycle changes,
 * otherwise /politicsmod info and the Economy tab will show wrong numbers.
 */
public record EconomySummary(
        int treasury,
        int maxBalance,
        int chunks,
        int directIncome,     // tax blocks outside cities
        int federalTaxIncome, // share of city tax block income
        int embassyIncome,
        int upkeep,
        String tributeTo,     // "" if no tribute is paid
        int tributeAmount,
        int vaults,
        int embassies,
        int activeEmbassies,
        int federalTaxRate,
        int salaryCost,       // gross salaries paid from the treasury
        int incomeTaxReturn,  // income tax withheld from those salaries, returned to the treasury
        int incomeTaxRate,
        int loanPayment,      // installments this country pays per cycle
        int loanIncome,       // installments other countries pay this one per cycle
        int debt,             // total still owed on loans
        int settlementIncome, // nation's share of colony and village tax (MineColonies, Millénaire)
        int nonCoreLoss       // tax blocks on non-core land bring this much less (nation and cities together)
) {
    public int income() {
        return directIncome + federalTaxIncome + embassyIncome + settlementIncome;
    }

    public int net() {
        return income() - upkeep - tributeAmount - salaryCost + incomeTaxReturn - loanPayment + loanIncome;
    }

    public static EconomySummary of(PoliticsManager mgr, String countryName) {
        Country country = mgr.getCountry(countryName);
        PoliticsConfig cfg = PoliticsConfig.get();

        int[] chunks = {0};
        mgr.forEachClaim((pos, color) -> {
            if (countryName.equals(mgr.getCountryNameAt(pos))) chunks[0]++;
        });

        Realm.TaxRates rates = Realm.taxRates(country, Realm.cities(mgr, country));
        int direct = 0;
        int nonCoreLoss = 0;
        Map<String, Integer> cityGross = new HashMap<>();
        for (Long posLong : country.taxBlocks) {
            ChunkPos pos = new ChunkPos(BlockPos.of(posLong));
            int income = Realm.taxBlockIncome(mgr, pos, rates);
            nonCoreLoss += Realm.taxBlockIncome(mgr, pos, rates, false) - income;
            String city = mgr.getCityAt(pos);
            if (city != null && country.cities.contains(city)) cityGross.merge(city, income, Integer::sum);
            else direct += income;
        }
        // Rounded per city, as the cycle does
        int federal = 0;
        if (country.federalTaxRate > 0) for (int gross : cityGross.values()) federal += gross * country.federalTaxRate / 100;

        int active = 0;
        for (String partner : country.embassyLinks.values()) {
            if (mgr.getCountry(partner) != null && !mgr.isAtWar(countryName, partner)) active++;
        }

        String tributeTo = "";
        int tributeAmount = 0;
        String[] tribute = mgr.getTribute(countryName);
        if (tribute != null && mgr.getCountry(tribute[0]) != null) {
            tributeTo = tribute[0];
            tributeAmount = country.taxBlocks.size() * cfg.incomePerTaxBlock * Integer.parseInt(tribute[1]) / 100;
        }

        int loanPayment = 0;
        int loanIncome = 0;
        for (Loan l : mgr.getLoans()) {
            if (l.borrower.equals(countryName)) loanPayment += Math.min(l.installment, l.remaining);
            if (l.lender.equals(countryName)) loanIncome += Math.min(l.installment, l.remaining);
        }

        int settlements = 0;
        for (net.krona.politicsmod.integration.settlement.Settlements.Income in
                : net.krona.politicsmod.integration.settlement.Settlements.incomeOf(mgr.overworld(), mgr, countryName)) {
            int amount = Realm.apply(in.amount(), rates.country());
            String city = mgr.getCityAt(in.center());
            boolean inCity = city != null && country.cities.contains(city);
            settlements += inCity ? (country.federalTaxRate > 0 ? amount * country.federalTaxRate / 100 : 0) : amount;
        }

        int salaryCost = 0;
        int incomeTaxReturn = 0;
        for (int salary : country.salaries.values()) {
            salaryCost += salary;
            incomeTaxReturn += WalletService.incomeTax(country, salary);
        }

        return new EconomySummary(
                country.balance,
                cfg.baseMaxBalance + country.vaultBlocks.size() * cfg.vaultBalanceBonus,
                chunks[0],
                direct,
                federal,
                active * PoliticsManager.embassyIncome(country),
                PoliticsManager.upkeep(country, chunks[0]),
                tributeTo,
                tributeAmount,
                country.vaultBlocks.size(),
                country.embassyBlocks.size(),
                active,
                country.federalTaxRate,
                salaryCost,
                incomeTaxReturn,
                country.incomeTaxRate,
                loanPayment,
                loanIncome,
                mgr.getDebt(countryName),
                settlements,
                nonCoreLoss);
    }
}

package net.krona.politicsmod.politics;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

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
        int federalTaxRate
) {
    public int income() {
        return directIncome + federalTaxIncome + embassyIncome;
    }

    public int net() {
        return income() - upkeep - tributeAmount;
    }

    public static EconomySummary of(PoliticsManager mgr, String countryName) {
        Country country = mgr.getCountry(countryName);
        PoliticsConfig cfg = PoliticsConfig.get();

        int[] chunks = {0};
        mgr.forEachClaim((pos, color) -> {
            if (countryName.equals(mgr.getCountryNameAt(pos))) chunks[0]++;
        });

        int direct = 0;
        int cityGross = 0;
        for (Long posLong : country.taxBlocks) {
            String city = mgr.getCityAt(new ChunkPos(BlockPos.of(posLong)));
            if (city != null && country.cities.contains(city)) cityGross += cfg.incomePerTaxBlock;
            else direct += cfg.incomePerTaxBlock;
        }
        int federal = country.federalTaxRate > 0 ? cityGross * country.federalTaxRate / 100 : 0;

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

        return new EconomySummary(
                country.balance,
                cfg.baseMaxBalance + country.vaultBlocks.size() * cfg.vaultBalanceBonus,
                chunks[0],
                direct,
                federal,
                active * cfg.embassyIncomeBonus,
                chunks[0] * cfg.upkeepPerChunk,
                tributeTo,
                tributeAmount,
                country.vaultBlocks.size(),
                country.embassyBlocks.size(),
                active,
                country.federalTaxRate);
    }
}

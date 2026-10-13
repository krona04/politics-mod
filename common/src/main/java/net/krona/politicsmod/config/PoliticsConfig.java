package net.krona.politicsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.platform.PlatformHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server balance config: config/politicsmod.json.
 *
 * Created on first launch with default values. Keys missing from the file
 * (for example after a mod update) fall back to defaults and are written back.
 * Reload without a restart: /politicsmod reload.
 */
public final class PoliticsConfig {

    private static final String FILE_NAME = Politicsmod.MODID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static PoliticsConfig instance = new PoliticsConfig();

    /** Version of the default values; older files get the new balance where they still had the old defaults. */
    private static final int CURRENT_VERSION = 3;
    public int configVersion = CURRENT_VERSION;

    // ── Territory ────────────────────────────────────────────────────────
    /** Treasury of a newly founded country, $. */
    public int startingTreasury = 2_500;
    /** Price of a country chunk while the country is small, $. */
    public int countryChunkPrice = 300;
    /** Each chunk the country owns above chunkPriceGrowthStart makes the next one this much dearer, %. */
    public int chunkPriceGrowthPercent = 2;
    /** Size (chunks) up to which the chunk price stays at countryChunkPrice. A new country owns 25. */
    public int chunkPriceGrowthStart = 25;
    /** Upper limit of a country chunk price, $. */
    public int maxChunkPrice = 5_000;
    /** A chunk that does not touch the country's land (an outpost) costs this many times more. */
    public int outpostPriceMultiplier = 3;
    /** Price of adding a chunk to a city, $. */
    public int cityChunkPrice = 100;
    /** Upkeep per chunk per economy cycle, $. */
    public int upkeepPerChunk = 2;
    /** Chunks every country keeps without upkeep (the capital's core). */
    public int upkeepFreeChunks = 9;

    // ── Economy ──────────────────────────────────────────────────────────
    /** Economy cycle length in ticks (20 ticks = 1 second): 3 minutes. */
    public int economyCycleTicks = 3_600;
    /** At dawn every country gets one chat report with the totals of the past day's cycles. */
    public boolean economyDailyReport = true;
    /** Income per tax block per cycle, $. */
    public int incomePerTaxBlock = 16;
    /** Maximum tax blocks per chunk. */
    public int maxTaxBlocksPerChunk = 2;
    /** Treasury limit without vaults, $. */
    public int baseMaxBalance = 10_000;
    /** Treasury limit bonus per vault, $. */
    public int vaultBalanceBonus = 10_000;
    /** Income per active embassy per cycle, $. */
    public int embassyIncomeBonus = 40;

    // ── Diplomacy ────────────────────────────────────────────────────────
    /** Cost of declaring war, $. */
    public int warCost = 500;
    /** Cooldown between war declarations, minutes. */
    public int warCooldownMinutes = 10;
    /** Tribute after surrender, % of income. */
    public int tributeRateDefault = 20;
    /** Federal tax (%) at which mayors may secede. */
    public int secedeThresholdPercent = 80;

    // ── Market (trade warehouse) ─────────────────────────────────────────
    /** Market fee taken from the seller, %. Allies trade without a fee. */
    public int marketFeePercent = 5;
    /** Maximum active listings per country. */
    public int maxListingsPerCountry = 27;
    /** Maximum active personal listings per player. */
    public int maxListingsPerPlayer = 9;
    /** Maximum listing price, $. */
    public int maxListingPrice = 1_000_000;

    // ── Personal wallets ─────────────────────────────────────────────────
    /** Money a player starts with the first time a wallet is created, $. */
    public int walletStartBalance = 100;
    /** Upper limit of a single wallet, $. */
    public int walletMaxBalance = 10_000_000;
    /** How many operations each wallet keeps in its history. */
    public int walletHistorySize = 50;
    /** Maximum salary the leader can assign to one citizen per cycle, $. */
    public int maxSalary = 10_000;
    /** Income tax a new country starts with, %. */
    public int defaultIncomeTaxPercent = 10;
    /** Highest income tax a leader can set, %. */
    public int maxIncomeTaxPercent = 50;

    // ── Currency exchange ────────────────────────────────────────────────
    /** Players can turn an item into wallet money (command or right-click on a vault). */
    public boolean exchangeEnabled = true;
    /** Item accepted by the exchange. */
    public String exchangeItem = "minecraft:emerald";
    /** Money per item, $. */
    public int exchangeRate = 10;
    /** Allow turning money back into items. Off by default: tax income would otherwise mint items. */
    public boolean exchangeWithdrawEnabled = false;

    // ── Protection ───────────────────────────────────────────────────────
    /** Creative players ignore territory protection (0.3 behaviour). Operators always do. */
    public boolean creativeBypassesProtection = false;
    /** Server defaults for protection flags; each country and city can override them. */
    public boolean flagDefaultPvp = true;
    public boolean flagDefaultExplosions = false;
    public boolean flagDefaultMobExplosions = false;
    public boolean flagDefaultFireSpread = false;
    public boolean flagDefaultItemPickup = true;
    public boolean flagDefaultEntry = true;

    // ── Plots ────────────────────────────────────────────────────────────
    /** Maximum plots one player can own or rent across the server. */
    public int maxPlotsPerPlayer = 3;

    // ── Roles ────────────────────────────────────────────────────────────
    /**
     * What each role may do besides building. The leader can always do everything.
     * Actions: INVITE, KICK, CLAIM_CITY, MANAGE_EMBASSIES, SPEND_TREASURY, BUY_TERRITORY, MANAGE_PLOTS, SELL_FOR_STATE.
     */
    public java.util.Map<String, java.util.List<String>> rolePermissions = defaultRolePermissions();

    // ── Elections ────────────────────────────────────────────────────────
    /** How long an election lasts, minutes. */
    public int electionDurationMinutes = 10;
    /** Minimum time between two elections in one country, minutes. */
    public int electionCooldownMinutes = 60;
    /** Share of citizens (%) whose petition starts a leader election. */
    public int petitionPercent = 50;

    // ── Laws ─────────────────────────────────────────────────────────────
    /** Minimum time between two law changes in one country, minutes. */
    public int lawChangeCooldownMinutes = 30;
    /** Martial law: tax income is lowered by this share, %. */
    public int martialLawIncomePenaltyPercent = 30;
    /** Martial law: sieges against the country take this many times longer. */
    public int martialLawSiegeMultiplier = 2;
    /** Free trade: embassy income is raised by this share, %. */
    public int freeTradeEmbassyBonusPercent = 50;

    // ── Sieges ───────────────────────────────────────────────────────────
    /** Cost of starting a siege, $. */
    public int siegeCost = 250;
    /** How long attackers must hold a chunk to capture it, seconds. */
    public int siegeHoldSeconds = 180;
    /** A siege fails after this many seconds without attackers in the chunk. */
    public int siegeGraceSeconds = 60;
    /** Sieges one country can run at the same time. */
    public int maxSiegesPerCountry = 1;

    // ── Debt and loans ───────────────────────────────────────────────────
    /** How far a treasury may go below zero, $. Upkeep and loan payments stop at this limit. */
    public int maxDebt = 50_000;
    /** Highest interest a loan may have, %. */
    public int maxLoanInterestPercent = 50;
    /** Longest repayment period of a loan, economy cycles. */
    public int maxLoanCycles = 140;

    // ── News ─────────────────────────────────────────────────────────────
    /** How many news entries the server keeps. */
    public int newsMaxEntries = 100;

    // ── Maps ─────────────────────────────────────────────────────────────
    /** Show country borders on BlueMap and Dynmap when they are installed. */
    public boolean webMapsEnabled = true;
    /** How often web map layers are refreshed after a change, seconds. */
    public int webMapUpdateSeconds = 30;

    // ── Settlement mods (NeoForge) ───────────────────────────────────────
    /** MineColonies: colonies count as settlements of the country their owner belongs to. */
    public boolean minecoloniesEnabled = true;
    /** Tax a colony of a citizen pays per colonist each cycle, $. */
    public int colonyTaxPerCitizen = 4;
    /** Happy colonies pay more: ×0.5 at happiness 0, ×1 at 5, ×1.5 at 10. */
    public boolean colonyTaxFollowsHappiness = true;
    /** Citizens and allies get the Friend rank in a citizen's colony, enemies at war the Hostile one (its guards attack them). */
    public boolean colonyRanksFollowDiplomacy = true;
    /** Millénaire: villages a citizen controls count as settlements of their country. */
    public boolean millenaireEnabled = true;
    /** Tax a Millénaire village a citizen controls pays per villager each cycle, $. */
    public int villageTaxPerVillager = 2;
    /** Reputation a citizen needs in a village before the leader can annex it (8192 = friend of the village). */
    public int villageAnnexReputation = 8192;
    /** An annexed village breaks away when no citizen keeps at least this reputation there (1024 = regular visitor). */
    public int villageKeepReputation = 1024;
    /** Annexed villages pay this share of the full village tax, %. */
    public int annexedVillageTributePercent = 50;
    /** Countries cannot claim the land of other players' colonies or of Millénaire villages they don't control. */
    public boolean settlementsBlockClaims = true;

    // ── Guide ────────────────────────────────────────────────────────────
    /** Give the in-game guide book to every player the first time they join. */
    public boolean giveGuideOnFirstJoin = true;

    // ── Updates ──────────────────────────────────────────────────────────
    /** Once per launch the client asks Modrinth for a newer release of the mod and says so in chat. */
    public boolean checkForUpdates = true;
    /** On joining a world or a server: a welcome the first time, then a line about supporting the mod on Ko-fi. */
    public boolean showSupportMessage = true;

    // ── Radar ────────────────────────────────────────────────────────────
    /** Radar detection radius, chunks. */
    public int radarRangeChunks = 5;
    /** How often radars check for enemies, ticks. */
    public int radarCheckTicks = 100;

    public static PoliticsConfig get() {
        return instance;
    }

    public long warCooldownMs() {
        return warCooldownMinutes * 60_000L;
    }

    public long electionDurationMs() {
        return electionDurationMinutes * 60_000L;
    }

    public long electionCooldownMs() {
        return electionCooldownMinutes * 60_000L;
    }

    public long lawCooldownMs() {
        return lawChangeCooldownMinutes * 60_000L;
    }

    /** Loads the config from disk. Returns false if the file is broken (previous values are kept). */
    public static boolean load() {
        Path path = PlatformHelper.getConfigDirectory().resolve(FILE_NAME);

        PoliticsConfig loaded = new PoliticsConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                PoliticsConfig fromFile = GSON.fromJson(json, PoliticsConfig.class);
                if (fromFile != null) {
                    loaded = fromFile;
                    // Files from 0.4 and older have no version key
                    loaded.configVersion = json.has("configVersion") ? json.get("configVersion").getAsInt() : 1;
                    loaded.migrate();
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                Politicsmod.LOGGER.error("Could not read {} - keeping previous values. Fix the JSON and run /politicsmod reload", path, e);
                return false;
            }
        }

        loaded.validate();
        instance = loaded;
        save(path, loaded);
        return true;
    }

    /**
     * 0.4.1 rebalanced the economy. A server that kept the old defaults gets the new ones; values the owner
     * changed by hand stay as they are.
     */
    private void migrate() {
        if (configVersion < 2) {
            int changed = 0;
            if (countryChunkPrice == 500) { countryChunkPrice = 300; changed++; }
            if (upkeepPerChunk == 10) { upkeepPerChunk = 5; changed++; }
            if (incomePerTaxBlock == 50) { incomePerTaxBlock = 40; changed++; }
            if (maxTaxBlocksPerChunk == 4) { maxTaxBlocksPerChunk = 2; changed++; }
            Politicsmod.LOGGER.info("Config updated to the 0.4.1 economy balance ({} old default values replaced)", changed);
        }
        // The cycle became 3 minutes instead of 8⅓: amounts per cycle shrink so the economy per hour stays the same.
        // Only when the cycle itself was still the default; a server with its own cycle keeps its own amounts.
        if (configVersion < 3 && economyCycleTicks == 10_000) {
            economyCycleTicks = 3_600;
            if (incomePerTaxBlock == 40) incomePerTaxBlock = 16;
            if (upkeepPerChunk == 5) upkeepPerChunk = 2;
            if (embassyIncomeBonus == 100) embassyIncomeBonus = 40;
            if (colonyTaxPerCitizen == 10) colonyTaxPerCitizen = 4;
            if (villageTaxPerVillager == 5) villageTaxPerVillager = 2;
            if (maxLoanCycles == 50) maxLoanCycles = 140;
            Politicsmod.LOGGER.info("Config updated: economy cycle is now 3 minutes, amounts per cycle scaled down to match");
        }
        if (configVersion < 3 && walletHistorySize == 20) walletHistorySize = 50;
        configVersion = CURRENT_VERSION;
    }

    /** Clamps values that would break the economy (division by zero, negative prices). */
    private void validate() {
        startingTreasury       = Math.max(0, startingTreasury);
        countryChunkPrice      = Math.max(0, countryChunkPrice);
        chunkPriceGrowthPercent = clamp(chunkPriceGrowthPercent, 0, 100);
        chunkPriceGrowthStart  = Math.max(0, chunkPriceGrowthStart);
        maxChunkPrice          = Math.max(countryChunkPrice, maxChunkPrice);
        outpostPriceMultiplier = clamp(outpostPriceMultiplier, 1, 100);
        cityChunkPrice         = Math.max(0, cityChunkPrice);
        upkeepPerChunk         = Math.max(0, upkeepPerChunk);
        upkeepFreeChunks       = Math.max(0, upkeepFreeChunks);
        colonyTaxPerCitizen    = Math.max(0, colonyTaxPerCitizen);
        villageTaxPerVillager  = Math.max(0, villageTaxPerVillager);
        villageKeepReputation  = Math.min(villageKeepReputation, villageAnnexReputation);
        annexedVillageTributePercent = clamp(annexedVillageTributePercent, 0, 100);
        economyCycleTicks      = Math.max(20, economyCycleTicks);
        incomePerTaxBlock      = Math.max(0, incomePerTaxBlock);
        maxTaxBlocksPerChunk   = Math.max(1, maxTaxBlocksPerChunk);
        baseMaxBalance         = Math.max(0, baseMaxBalance);
        vaultBalanceBonus      = Math.max(0, vaultBalanceBonus);
        embassyIncomeBonus     = Math.max(0, embassyIncomeBonus);
        warCost                = Math.max(0, warCost);
        warCooldownMinutes     = Math.max(0, warCooldownMinutes);
        tributeRateDefault     = clamp(tributeRateDefault, 0, 100);
        secedeThresholdPercent = clamp(secedeThresholdPercent, 0, 100);
        marketFeePercent       = clamp(marketFeePercent, 0, 100);
        maxListingsPerCountry  = Math.max(1, maxListingsPerCountry);
        maxListingPrice        = Math.max(1, maxListingPrice);
        maxListingsPerPlayer   = Math.max(1, maxListingsPerPlayer);
        walletStartBalance     = Math.max(0, walletStartBalance);
        walletMaxBalance       = Math.max(1, walletMaxBalance);
        walletHistorySize      = clamp(walletHistorySize, 1, 200);
        maxSalary              = Math.max(0, maxSalary);
        maxIncomeTaxPercent    = clamp(maxIncomeTaxPercent, 0, 100);
        defaultIncomeTaxPercent = clamp(defaultIncomeTaxPercent, 0, maxIncomeTaxPercent);
        exchangeRate           = Math.max(1, exchangeRate);
        if (exchangeItem == null) exchangeItem = "minecraft:emerald";
        maxPlotsPerPlayer      = Math.max(0, maxPlotsPerPlayer);
        if (rolePermissions == null) rolePermissions = defaultRolePermissions();
        electionDurationMinutes = Math.max(1, electionDurationMinutes);
        electionCooldownMinutes = Math.max(0, electionCooldownMinutes);
        petitionPercent        = clamp(petitionPercent, 1, 100);
        lawChangeCooldownMinutes = Math.max(0, lawChangeCooldownMinutes);
        martialLawIncomePenaltyPercent = clamp(martialLawIncomePenaltyPercent, 0, 100);
        martialLawSiegeMultiplier = clamp(martialLawSiegeMultiplier, 1, 10);
        freeTradeEmbassyBonusPercent = clamp(freeTradeEmbassyBonusPercent, 0, 1000);
        siegeCost              = Math.max(0, siegeCost);
        siegeHoldSeconds       = Math.max(10, siegeHoldSeconds);
        siegeGraceSeconds      = Math.max(5, siegeGraceSeconds);
        maxSiegesPerCountry    = Math.max(1, maxSiegesPerCountry);
        maxDebt                = Math.max(0, maxDebt);
        maxLoanInterestPercent = clamp(maxLoanInterestPercent, 0, 1000);
        maxLoanCycles          = Math.max(1, maxLoanCycles);
        newsMaxEntries         = clamp(newsMaxEntries, 10, 1000);
        webMapUpdateSeconds    = Math.max(5, webMapUpdateSeconds);
        radarRangeChunks       = clamp(radarRangeChunks, 1, 32);
        radarCheckTicks        = Math.max(20, radarCheckTicks);
    }

    private static java.util.Map<String, java.util.List<String>> defaultRolePermissions() {
        java.util.Map<String, java.util.List<String>> map = new java.util.LinkedHashMap<>();
        map.put("MAYOR", new java.util.ArrayList<>(java.util.List.of("INVITE", "KICK", "CLAIM_CITY", "MANAGE_EMBASSIES",
                "SPEND_TREASURY", "BUY_TERRITORY", "MANAGE_PLOTS", "SELL_FOR_STATE")));
        map.put("CITIZEN", new java.util.ArrayList<>(java.util.List.of("SELL_FOR_STATE")));
        return map;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void save(Path path, PoliticsConfig config) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Politicsmod.LOGGER.error("Could not write {}", path, e);
        }
    }
}

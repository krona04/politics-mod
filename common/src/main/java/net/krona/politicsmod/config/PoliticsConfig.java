package net.krona.politicsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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

    // ── Territory ────────────────────────────────────────────────────────
    /** Price of a country chunk, $. */
    public int countryChunkPrice = 500;
    /** Price of adding a chunk to a city, $. */
    public int cityChunkPrice = 100;
    /** Upkeep per chunk per economy cycle, $. */
    public int upkeepPerChunk = 10;

    // ── Economy ──────────────────────────────────────────────────────────
    /** Economy cycle length in ticks (20 ticks = 1 second). */
    public int economyCycleTicks = 10_000;
    /** Income per tax block per cycle, $. */
    public int incomePerTaxBlock = 50;
    /** Maximum tax blocks per chunk. */
    public int maxTaxBlocksPerChunk = 4;
    /** Treasury limit without vaults, $. */
    public int baseMaxBalance = 10_000;
    /** Treasury limit bonus per vault, $. */
    public int vaultBalanceBonus = 10_000;
    /** Income per active embassy per cycle, $. */
    public int embassyIncomeBonus = 100;

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
    /** Maximum listing price, $. */
    public int maxListingPrice = 1_000_000;

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

    /** Loads the config from disk. Returns false if the file is broken (previous values are kept). */
    public static boolean load() {
        Path path = PlatformHelper.getConfigDirectory().resolve(FILE_NAME);

        PoliticsConfig loaded = new PoliticsConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                PoliticsConfig fromFile = GSON.fromJson(reader, PoliticsConfig.class);
                if (fromFile != null) {
                    loaded = fromFile;
                }
            } catch (IOException | JsonParseException e) {
                Politicsmod.LOGGER.error("Could not read {} - keeping previous values. Fix the JSON and run /politicsmod reload", path, e);
                return false;
            }
        }

        loaded.validate();
        instance = loaded;
        save(path, loaded);
        return true;
    }

    /** Clamps values that would break the economy (division by zero, negative prices). */
    private void validate() {
        countryChunkPrice      = Math.max(0, countryChunkPrice);
        cityChunkPrice         = Math.max(0, cityChunkPrice);
        upkeepPerChunk         = Math.max(0, upkeepPerChunk);
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
        radarRangeChunks       = clamp(radarRangeChunks, 1, 32);
        radarCheckTicks        = Math.max(20, radarCheckTicks);
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

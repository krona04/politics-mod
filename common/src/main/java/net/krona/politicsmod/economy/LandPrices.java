package net.krona.politicsmod.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.world.level.ChunkPos;

/**
 * Prices of land and its upkeep. The same rules are sent to the client so the map can show
 * what a selection will cost before the leader buys it.
 *
 * A chunk costs the base price while the country is small; every chunk above the growth start makes the next
 * one dearer, up to a cap. A chunk that does not touch the country's land (an outpost) costs several times more.
 */
public final class LandPrices {

    private LandPrices() {
    }

    /** The configured price rules; a plain record so it can travel in a packet. */
    public record Rules(int base, int growthPercent, int growthStart, int max, int outpostMultiplier) {

        public static Rules current() {
            PoliticsConfig cfg = PoliticsConfig.get();
            return new Rules(cfg.countryChunkPrice, cfg.chunkPriceGrowthPercent, cfg.chunkPriceGrowthStart,
                    cfg.maxChunkPrice, cfg.outpostPriceMultiplier);
        }

        /** Price of the next chunk for a country that owns {@code owned} chunks. */
        public int price(int owned, boolean touching) {
            long grown = (long) base * (100 + (long) growthPercent * Math.max(0, owned - growthStart)) / 100;
            long price = Math.min(Math.max(base, max), grown);
            if (!touching) price *= outpostMultiplier;
            return (int) Math.min(Integer.MAX_VALUE, price);
        }
    }

    /**
     * Price of a country chunk at this position for this country, right now. Land of the country's own colonies
     * and villages is free: it is theirs already.
     */
    public static int countryChunk(PoliticsManager mgr, String country, ChunkPos pos) {
        if (net.krona.politicsmod.integration.settlement.Settlements.ownLand(mgr.overworld(), mgr, country, pos)) return 0;
        return Rules.current().price(mgr.getChunksOf(country).size(), touches(mgr, country, pos));
    }

    /** True when one of the four neighbours of the chunk belongs to the country. */
    public static boolean touches(PoliticsManager mgr, String country, ChunkPos pos) {
        return country.equals(mgr.getCountryNameAt(new ChunkPos(pos.x + 1, pos.z)))
                || country.equals(mgr.getCountryNameAt(new ChunkPos(pos.x - 1, pos.z)))
                || country.equals(mgr.getCountryNameAt(new ChunkPos(pos.x, pos.z + 1)))
                || country.equals(mgr.getCountryNameAt(new ChunkPos(pos.x, pos.z - 1)));
    }

    /** Upkeep per cycle of a country with this many chunks: the capital's core is free. */
    public static int upkeep(int chunks) {
        PoliticsConfig cfg = PoliticsConfig.get();
        return Math.max(0, chunks - cfg.upkeepFreeChunks) * cfg.upkeepPerChunk;
    }
}

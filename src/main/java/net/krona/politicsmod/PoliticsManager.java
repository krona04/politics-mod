package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncChunkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

public class PoliticsManager extends SavedData {

    private final Map<String, CountryData> countries = new HashMap<>(); // Данные о странах
    private final Map<ChunkPos, String> chunkOwners = new HashMap<>(); // Чанк -> Страна
    private final Map<ChunkPos, String> chunkCity = new HashMap<>();   // Чанк -> Город

    private final Map<String, List<String>> cities = new HashMap<>(); // Страна -> Список городов
    private final Map<UUID, String> playerCountries = new HashMap<>(); // Игрок -> Страна
    private final Map<String, Integer> cityColors = new HashMap<>();   // Город -> Цвет

    public static class CountryData {
        public String name;
        public UUID owner;
        public int balance;
        public String capital = "";
        public String flagUrl = "";

        public CountryData(String name, UUID owner, int balance) {
            this.name = name;
            this.owner = owner;
            this.balance = balance;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            if (owner != null) tag.putUUID("owner", owner);
            tag.putInt("balance", balance);
            tag.putString("capital", capital != null ? capital : "");
            tag.putString("flagUrl", flagUrl != null ? flagUrl : "");
            return tag;
        }

        public static CountryData load(CompoundTag tag) {
            String name = tag.getString("name");
            UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
            int balance = tag.getInt("balance");
            CountryData data = new CountryData(name, owner, balance);
            data.capital = tag.getString("capital");
            data.flagUrl = tag.getString("flagUrl");
            return data;
        }
    }

    public static PoliticsManager get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(new Factory<>(
                    PoliticsManager::new, PoliticsManager::load, null
            ), "politicsmod_data");
        }
        return null;
    }

    public PoliticsManager() {}

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag countryList = new ListTag();
        for (CountryData data : countries.values()) {
            CompoundTag cTag = data.save();

            ListTag citiesTag = new ListTag();
            if (cities.containsKey(data.name)) {
                for (String city : cities.get(data.name)) {
                    CompoundTag ct = new CompoundTag();
                    ct.putString("n", city);
                    if (cityColors.containsKey(city)) ct.putInt("color", cityColors.get(city));
                    citiesTag.add(ct);
                }
            }
            cTag.put("cities", citiesTag);
            countryList.add(cTag);
        }
        tag.put("countries", countryList);

        ListTag claimsList = new ListTag();
        for (Map.Entry<ChunkPos, String> entry : chunkOwners.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", entry.getKey().toLong());
            t.putString("country", entry.getValue());
            String city = chunkCity.get(entry.getKey());
            if (city != null && !city.isEmpty()) t.putString("city", city);
            claimsList.add(t);
        }
        tag.put("claims", claimsList);

        return tag;
    }

    public static PoliticsManager load(CompoundTag tag, HolderLookup.Provider provider) {
        PoliticsManager manager = new PoliticsManager();

        ListTag cList = tag.getList("countries", Tag.TAG_COMPOUND);
        for (Tag t : cList) {
            CompoundTag c = (CompoundTag) t;
            CountryData data = CountryData.load(c);

            manager.countries.put(data.name, data);
            if (data.owner != null) manager.playerCountries.put(data.owner, data.name);

            ListTag cityList = c.getList("cities", Tag.TAG_COMPOUND);
            List<String> loadedCities = new ArrayList<>();
            for (Tag ct : cityList) {
                CompoundTag cityTag = (CompoundTag) ct;
                String cityName = cityTag.getString("n");
                loadedCities.add(cityName);
                if (cityTag.contains("color")) manager.cityColors.put(cityName, cityTag.getInt("color"));
            }
            manager.cities.put(data.name, loadedCities);
        }

        ListTag claimsList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (Tag t : claimsList) {
            CompoundTag entry = (CompoundTag) t;
            ChunkPos pos = new ChunkPos(entry.getLong("pos"));
            manager.chunkOwners.put(pos, entry.getString("country"));
            if (entry.contains("city")) manager.chunkCity.put(pos, entry.getString("city"));
        }
        return manager;
    }

    public void saveData() {
        this.setDirty();
    }

    public String getFlagUrl(String country) {
        if (countries.containsKey(country)) return countries.get(country).flagUrl;
        return "";
    }

    public void setFlagUrl(String country, String url) {
        if (countries.containsKey(country)) {
            countries.get(country).flagUrl = url;
            setDirty();
        }
    }

    public String getCountryByOwner(UUID uuid) {
        return playerCountries.get(uuid);
    }

    public String getCountryAt(ChunkPos pos) { return chunkOwners.get(pos); }
    public String getCityAt(ChunkPos pos) { return chunkCity.get(pos); }

    public int getBalance(String country) {
        return countries.containsKey(country) ? countries.get(country).balance : 0;
    }

    public List<String> getCities(String country) { return cities.getOrDefault(country, new ArrayList<>()); }

    public boolean hasCity(String country, String city) {
        List<String> list = cities.get(country);
        return list != null && list.contains(city);
    }

    public int getColorForChunk(ChunkPos pos) {
        String city = chunkCity.get(pos);
        if (city != null && !city.isEmpty() && cityColors.containsKey(city)) return cityColors.get(city);
        if (chunkOwners.containsKey(pos)) return 0x00AA00; // Зеленый для страны без города
        return 0;
    }

    public void syncToPlayer(ServerPlayer player) {
        forEachClaim((pos, color) -> PacketDistributor.sendToPlayer(player, new SyncChunkPayload(pos, color)));
    }

    public void forEachClaim(java.util.function.BiConsumer<ChunkPos, Integer> action) {
        for (ChunkPos pos : chunkOwners.keySet()) action.accept(pos, getColorForChunk(pos));
    }

    private void claimAreaInternal(ChunkPos center, int radius, String country, String city) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos p = new ChunkPos(center.x + x, center.z + z);
                chunkOwners.put(p, country);
                if (city != null && !city.isEmpty()) chunkCity.put(p, city);
                else chunkCity.remove(p);
            }
        }
    }

    public void claimChunk(ChunkPos center, int radius, String countryName, String cityName) {
        claimAreaInternal(center, radius, countryName, cityName);
        setDirty();
    }

    public static void createCountry(Level level, BlockPos center, Player owner, String countryName) {
        PoliticsManager manager = get(level);
        if (manager == null) return;

        ChunkPos centerChunk = new ChunkPos(center);

        if (manager.countries.containsKey(countryName)) {
            owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists")); // "Имя занято"
            return;
        }

        if (manager.playerCountries.containsKey(owner.getUUID())) {
            String existing = manager.playerCountries.get(owner.getUUID());
            owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists_error", existing).withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        // Создаем данные
        CountryData newData = new CountryData(countryName, owner.getUUID(), 1000);
        manager.countries.put(countryName, newData);
        manager.playerCountries.put(owner.getUUID(), countryName);
        manager.cities.put(countryName, new ArrayList<>());

        manager.claimAreaInternal(centerChunk, 2, countryName, null);

        manager.setDirty();
        manager.syncToPlayer((ServerPlayer) owner);
    }

    public void foundNewCity(BlockPos center, Player player, String cityName) {
        String country = getCountryByOwner(player.getUUID());
        if (country == null) return;

        ChunkPos centerChunk = new ChunkPos(center);
        claimAreaInternal(centerChunk, 1, country, cityName);

        createCityEntry(country, cityName);
        cityColors.put(cityName, 0xFFD700); // Золотой цвет

        setDirty();
        syncToPlayer((ServerPlayer) player);
    }

    public void createCityEntry(String country, String city) {
        cities.computeIfAbsent(country, k -> new ArrayList<>()).add(city);
    }

    public void createCity(String countryName, String cityName) {
        if (!cities.containsKey(countryName)) return;
        createCityEntry(countryName, cityName);
        setDirty();
    }

    public void setCapital(String country, String newCapitalCity) {
        if (!hasCity(country, newCapitalCity)) return;
        if (countries.containsKey(country)) {
            countries.get(country).capital = newCapitalCity;
            cityColors.put(newCapitalCity, 0xFFD700);
            setDirty();
        }
    }

    public boolean deleteCountry(String countryName) {
        if (!countries.containsKey(countryName)) return false;

        List<ChunkPos> chunksToRemove = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> entry : chunkOwners.entrySet()) {
            if (entry.getValue().equals(countryName)) {
                chunksToRemove.add(entry.getKey());
            }
        }

        for (ChunkPos pos : chunksToRemove) {
            chunkOwners.remove(pos);
            chunkCity.remove(pos);

            PacketDistributor.sendToAllPlayers(new SyncChunkPayload(pos, 0));
        }

        cities.remove(countryName);

        CountryData data = countries.remove(countryName);
        if (data != null && data.owner != null) {
            UUID ownerUUID = data.owner;
            playerCountries.remove(ownerUUID);

            net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(ownerUUID);
                if (player != null) {
                    syncToPlayer(player);
                }
            }
        }

        setDirty();
        return true;
    }

    public boolean renameCountry(String oldName, String newName) {
        return false;
    }

    public boolean renameCity(String country, String oldCity, String newCity) {
        return false;
    }

    public void collectTaxes(ServerLevel level) {
        }
}
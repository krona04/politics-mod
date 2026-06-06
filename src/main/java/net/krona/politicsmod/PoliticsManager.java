package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncChunkPayload;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PoliticsManager extends SavedData {

    // ── КОНСТАНТЫ ─────────────────────────────────────────────────────────
    public static final int  UPKEEP_PER_CHUNK     = 10;
    public static final int  INCOME_PER_TAX_BLOCK = 50;
    public static final int  WAR_COST             = 500;
    public static final long WAR_COOLDOWN_MS      = 10L * 60 * 1000;
    public static final int  TRIBUTE_RATE_DEFAULT = 20;
    public static final int  SECEDE_THRESHOLD     = 80; // % при котором можно сепарироваться
    public static final int  BASE_MAX_BALANCE     = 10_000; // максимум без хранилищ
    public static final int  VAULT_BALANCE_BONUS  = 10_000; // +$ за каждое хранилище
    public static final int  EMBASSY_INCOME_BONUS = 100;    // $ за активное посольство за цикл
    public static final int  RADAR_RANGE_CHUNKS   = 5;      // радиус обнаружения радара

    // ── ДАННЫЕ ────────────────────────────────────────────────────────────
    private final Map<String, Country>                      countries    = new HashMap<>();
    private final Map<ChunkPos, String>                     chunkOwners  = new HashMap<>();
    private final Map<ChunkPos, String>                     chunkCity    = new HashMap<>();
    private final Map<String, Map<String, DiplomacyStatus>> diplomacy    = new HashMap<>();
    private final Map<String, Long>                         warCooldowns = new HashMap<>();
    private final Map<String, String[]>                     tributes     = new HashMap<>();

    public PoliticsManager() {}

    // ── ИНСТАНС ───────────────────────────────────────────────────────────

    public static PoliticsManager get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PoliticsManager::new, PoliticsManager::load, null),
                "politicsmod_data");
        }
        return null;
    }

    // ── NBT СОХРАНЕНИЕ ───────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag countryList = new ListTag();
        for (Country c : countries.values()) countryList.add(c.save(new CompoundTag()));
        tag.put("countries", countryList);

        ListTag claimsList = new ListTag();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey().toLong());
            t.putString("country", e.getValue());
            String city = chunkCity.get(e.getKey());
            if (city != null && !city.isEmpty()) t.putString("city", city);
            claimsList.add(t);
        }
        tag.put("claims", claimsList);

        ListTag dipList = new ListTag();
        for (Map.Entry<String, Map<String, DiplomacyStatus>> outer : diplomacy.entrySet()) {
            for (Map.Entry<String, DiplomacyStatus> inner : outer.getValue().entrySet()) {
                if (outer.getKey().compareTo(inner.getKey()) < 0) {
                    CompoundTag r = new CompoundTag();
                    r.putString("a", outer.getKey()); r.putString("b", inner.getKey());
                    r.putString("s", inner.getValue().name());
                    dipList.add(r);
                }
            }
        }
        tag.put("diplomacy", dipList);

        ListTag tributeList = new ListTag();
        for (Map.Entry<String, String[]> e : tributes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("payer", e.getKey());
            t.putString("receiver", e.getValue()[0]);
            t.putInt("rate", Integer.parseInt(e.getValue()[1]));
            tributeList.add(t);
        }
        tag.put("tributes", tributeList);

        return tag;
    }

    // ── NBT ЗАГРУЗКА ─────────────────────────────────────────────────────

    public static PoliticsManager load(CompoundTag tag, HolderLookup.Provider provider) {
        PoliticsManager m = new PoliticsManager();

        for (Tag t : tag.getList("countries", Tag.TAG_COMPOUND)) {
            Country c = Country.load((CompoundTag) t);
            m.countries.put(c.getName(), c);
        }
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ChunkPos pos = new ChunkPos(e.getLong("pos"));
            m.chunkOwners.put(pos, e.getString("country"));
            if (e.contains("city")) m.chunkCity.put(pos, e.getString("city"));
        }
        if (tag.contains("diplomacy")) {
            for (int i = 0; i < tag.getList("diplomacy", Tag.TAG_COMPOUND).size(); i++) {
                CompoundTag r = tag.getList("diplomacy", Tag.TAG_COMPOUND).getCompound(i);
                String a = r.getString("a"), b = r.getString("b");
                DiplomacyStatus s = DiplomacyStatus.valueOf(r.getString("s"));
                m.diplomacy.computeIfAbsent(a, k -> new HashMap<>()).put(b, s);
                m.diplomacy.computeIfAbsent(b, k -> new HashMap<>()).put(a, s);
            }
        }
        if (tag.contains("tributes")) {
            for (int i = 0; i < tag.getList("tributes", Tag.TAG_COMPOUND).size(); i++) {
                CompoundTag t = tag.getList("tributes", Tag.TAG_COMPOUND).getCompound(i);
                m.tributes.put(t.getString("payer"),
                    new String[]{t.getString("receiver"), String.valueOf(t.getInt("rate"))});
            }
        }
        return m;
    }

    public void saveData() { this.setDirty(); }

    // ── ДИПЛОМАТИЯ ───────────────────────────────────────────────────────

    public DiplomacyStatus getDiplomacy(String a, String b) {
        return diplomacy.getOrDefault(a, Collections.emptyMap()).getOrDefault(b, DiplomacyStatus.NEUTRAL);
    }
    public void setDiplomacy(String a, String b, DiplomacyStatus status) {
        if (status == DiplomacyStatus.NEUTRAL) {
            Map<String, DiplomacyStatus> ma = diplomacy.get(a);
            if (ma != null) { ma.remove(b); if (ma.isEmpty()) diplomacy.remove(a); }
            Map<String, DiplomacyStatus> mb = diplomacy.get(b);
            if (mb != null) { mb.remove(a); if (mb.isEmpty()) diplomacy.remove(b); }
        } else {
            diplomacy.computeIfAbsent(a, k -> new HashMap<>()).put(b, status);
            diplomacy.computeIfAbsent(b, k -> new HashMap<>()).put(a, status);
        }
        setDirty();
    }
    public boolean isAtWar(String a, String b)  { return getDiplomacy(a, b) == DiplomacyStatus.WAR; }
    public boolean isAllied(String a, String b) { return getDiplomacy(a, b) == DiplomacyStatus.ALLIANCE; }

    public boolean isBorderChunk(ChunkPos pos, String owner) {
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            String n = chunkOwners.get(new ChunkPos(pos.x+d[0], pos.z+d[1]));
            if (n == null || !n.equals(owner)) return true;
        }
        return false;
    }
    public boolean canDeclareWar(String c) { Long l = warCooldowns.get(c); return l==null||System.currentTimeMillis()-l>=WAR_COOLDOWN_MS; }
    public long getWarCooldownTimestamp(String c) { return warCooldowns.getOrDefault(c, 0L); }
    public void recordWarDeclaration(String c)    { warCooldowns.put(c, System.currentTimeMillis()); }

    // ── ТРИБУТЫ ──────────────────────────────────────────────────────────

    public void setTribute(String payer, String receiver, int rate) { tributes.put(payer, new String[]{receiver, String.valueOf(rate)}); setDirty(); }
    public void removeTribute(String payer)             { tributes.remove(payer); setDirty(); }
    public String[] getTribute(String payer)            { return tributes.get(payer); }
    public Map<String, String[]> getAllTributes()        { return Collections.unmodifiableMap(tributes); }

    // ── ГОРОДСКИЕ МЭРЫ ───────────────────────────────────────────────────

    /** Назначить мэра города. */
    public void setCityMayor(String countryName, String cityName, UUID mayorUUID) {
        Country c = countries.get(countryName);
        if (c == null) return;
        // Один игрок — один город: убрать из других городов
        c.cityMayors.entrySet().removeIf(e -> e.getValue().equals(mayorUUID));
        c.cityMayors.put(cityName, mayorUUID);
        setDirty();
    }

    /** Вернуть, мэром какого города является игрок (или null). */
    public String getMayorCity(String countryName, UUID playerUUID) {
        Country c = countries.get(countryName);
        if (c == null) return null;
        for (Map.Entry<String, UUID> e : c.cityMayors.entrySet())
            if (e.getValue().equals(playerUUID)) return e.getKey();
        return null;
    }

    /** Получить баланс городской казны. */
    public int getCityBalance(String countryName, String cityName) {
        Country c = countries.get(countryName);
        return c != null ? c.cityBalances.getOrDefault(cityName, 0) : 0;
    }

    // ── ПОСОЛЬСТВА ────────────────────────────────────────────────────────────

    /**
     * Привязать посольство (по позиции блока) к стране-партнёру.
     * Возвращает false если такого блока нет в списке страны.
     */
    public boolean linkEmbassy(String countryName, long blockPosLong, String partnerCountry) {
        Country c = countries.get(countryName);
        if (c == null || !c.embassyBlocks.contains(blockPosLong)) return false;
        c.embassyLinks.put(blockPosLong, partnerCountry);
        setDirty();
        return true;
    }

    /** Отвязать посольство по позиции блока. */
    public boolean unlinkEmbassy(String countryName, long blockPosLong) {
        Country c = countries.get(countryName);
        if (c == null) return false;
        boolean removed = c.embassyLinks.remove(blockPosLong) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Найти посольство страны в чанке игрока (первый попавшийся). */
    public Long findEmbassyInChunk(String countryName, net.minecraft.world.level.ChunkPos chunk) {
        Country c = countries.get(countryName);
        if (c == null) return null;
        for (Long posLong : c.embassyBlocks) {
            if (new ChunkPos(BlockPos.of(posLong)).equals(chunk)) return posLong;
        }
        return null;
    }

    // ── ТЕРРИТОРИАЛЬНАЯ ТОРГОВЛЯ ──────────────────────────────────────────

    public void transferChunk(ChunkPos pos, String newCountry) {
        chunkOwners.put(pos, newCountry);
        chunkCity.remove(pos);
        setDirty();
    }

    // ── ГЕТТЕРЫ ───────────────────────────────────────────────────────────

    public Country getCountry(String name)        { return countries.get(name); }
    public Map<String, Country> getCountries()    { return Collections.unmodifiableMap(countries); }
    public Country getCountryAt(ChunkPos pos)     { String n = chunkOwners.get(pos); return n!=null ? countries.get(n) : null; }
    public String getCountryNameAt(ChunkPos pos)  { return chunkOwners.get(pos); }
    public String getCityAt(ChunkPos pos)         { return chunkCity.get(pos); }

    public String getCountryByOwner(UUID uuid) {
        for (Country c : countries.values()) if (c.getRole(uuid) == CountryRole.LEADER) return c.getName();
        return null;
    }
    public String getPlayerCountry(UUID id) {
        for (Country c : countries.values()) if (c.getRole(id) != CountryRole.GUEST) return c.getName();
        return null;
    }

    public String getFlagUrl(String c)           { return countries.containsKey(c) ? countries.get(c).flagUrl : ""; }
    public void setFlagUrl(String c, String url) { if (countries.containsKey(c)) { countries.get(c).flagUrl = url; setDirty(); } }
    public int getBalance(String c)              { return countries.containsKey(c) ? countries.get(c).balance : 0; }
    public List<String> getCities(String c)      { return countries.containsKey(c) ? countries.get(c).cities : new ArrayList<>(); }
    public boolean hasCity(String c, String city){ return countries.containsKey(c) && countries.get(c).cities.contains(city); }

    public int getColorForChunk(ChunkPos pos) {
        String city = chunkCity.get(pos);
        if (city != null && !city.isEmpty()) {
            String cn = chunkOwners.get(pos);
            if (cn != null && countries.containsKey(cn))
                return countries.get(cn).cityColors.getOrDefault(city, 0xFFD700);
        }
        return chunkOwners.containsKey(pos) ? 0x00AA00 : 0;
    }

    public void syncToPlayer(ServerPlayer player) {
        forEachClaim((pos, color) -> PacketDistributor.sendToPlayer(player, new SyncChunkPayload(pos, color)));
    }
    public void forEachClaim(java.util.function.BiConsumer<ChunkPos, Integer> action) {
        for (ChunkPos pos : chunkOwners.keySet()) action.accept(pos, getColorForChunk(pos));
    }

    // ── СОЗДАНИЕ И КЛЕЙМЫ ─────────────────────────────────────────────────

    private void claimAreaInternal(ChunkPos center, int radius, String country, String city) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos p = new ChunkPos(center.x+x, center.z+z);
                chunkOwners.put(p, country);
                if (city != null && !city.isEmpty()) chunkCity.put(p, city);
                else chunkCity.remove(p);
            }
        }
    }
    public void claimChunk(ChunkPos c, int r, String country, String city) { claimAreaInternal(c, r, country, city); setDirty(); }

    public static void createCountry(Level level, BlockPos center, Player owner, String name) {
        PoliticsManager m = get(level);
        if (m == null) return;
        if (m.countries.containsKey(name)) { owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists")); return; }
        if (m.getCountryByOwner(owner.getUUID()) != null) {
            owner.sendSystemMessage(Component.translatable("message.politicsmod.country_exists_error",
                m.getCountryByOwner(owner.getUUID())).withStyle(net.minecraft.ChatFormatting.RED)); return;
        }
        m.countries.put(name, new Country(name, owner.getUUID()));
        m.claimAreaInternal(new ChunkPos(center), 2, name, null);
        m.setDirty(); m.syncToPlayer((ServerPlayer) owner);
    }

    public void foundNewCity(BlockPos center, Player player, String cityName) {
        String cn = getCountryByOwner(player.getUUID());
        if (cn == null) return;
        claimAreaInternal(new ChunkPos(center), 1, cn, cityName);
        createCityEntry(cn, cityName);
        countries.get(cn).cityColors.put(cityName, 0xFFD700);
        countries.get(cn).cityBalances.put(cityName, 0); // инициализируем казну
        setDirty(); syncToPlayer((ServerPlayer) player);
    }

    // ── ЭКОНОМИЧЕСКИЙ ЦИКЛ ────────────────────────────────────────────────

    public void processEconomyCycle(ServerLevel level) {
        for (Country country : countries.values()) {
            // Подсчёт чанков
            int chunks = 0;
            for (String o : chunkOwners.values()) if (o.equals(country.getName())) chunks++;

            // Роутинг дохода TaxBlock: в город или в страну
            Map<String, Integer> thisCycleIncome = new HashMap<>();
            for (Long posLong : country.taxBlocks) {
                ChunkPos cp = new ChunkPos(BlockPos.of(posLong));
                String city = chunkCity.get(cp);
                if (city != null && country.cities.contains(city)) {
                    thisCycleIncome.merge(city, INCOME_PER_TAX_BLOCK, Integer::sum);
                } else {
                    country.balance += INCOME_PER_TAX_BLOCK;
                }
            }

            // Применяем федеральный налог: часть городского дохода -> страна
            for (Map.Entry<String, Integer> e : thisCycleIncome.entrySet()) {
                String city    = e.getKey();
                int cycleIncome = e.getValue();
                int fedTax     = country.federalTaxRate > 0 ? cycleIncome * country.federalTaxRate / 100 : 0;
                country.cityBalances.merge(city, cycleIncome - fedTax, Integer::sum);
                country.balance += fedTax;
            }

            // Содержание территории
            country.balance -= chunks * UPKEEP_PER_CHUNK;
            if (country.balance < 0) country.balance = 0;
        }

        // Трибутные платежи (отдельный проход)
        for (Map.Entry<String, String[]> e : tributes.entrySet()) {
            Country payer    = countries.get(e.getKey());
            Country receiver = countries.get(e.getValue()[0]);
            int rate         = Integer.parseInt(e.getValue()[1]);
            if (payer == null || receiver == null) continue;
            int amount = payer.taxBlocks.size() * INCOME_PER_TAX_BLOCK * rate / 100;
            if (amount > 0) {
                payer.balance    = Math.max(0, payer.balance - amount);
                receiver.balance += amount;
            }
        }

        // ── Посольства: бонусный доход за активные дипломатические связи
        for (Country country : countries.values()) {
            for (String partner : country.embassyLinks.values()) {
                if (countries.containsKey(partner) && !isAtWar(country.getName(), partner)) {
                    country.balance += EMBASSY_INCOME_BONUS;
                }
            }
        }

        // ── Vault: ограничение максимального баланса
        for (Country country : countries.values()) {
            int maxBalance = BASE_MAX_BALANCE + country.vaultBlocks.size() * VAULT_BALANCE_BONUS;
            if (country.balance > maxBalance) country.balance = maxBalance;
        }

        setDirty();
    }

    // ── СЕПАРАТИЗМ ────────────────────────────────────────────────────────

    /**
     * Мэр откалывает свой город в отдельную страну.
     * Возвращает false если город не найден, имя занято или мэр не назначен.
     */
    public boolean secede(String oldCountryName, String cityName) {
        Country old = countries.get(oldCountryName);
        if (old == null || !old.cities.contains(cityName)) return false;

        UUID newLeaderUUID = old.cityMayors.get(cityName);
        if (newLeaderUUID == null) return false;
        if (countries.containsKey(cityName)) return false; // имя занято

        // Собираем чанки города
        List<ChunkPos> cityChunks = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet()) {
            if (e.getValue().equals(oldCountryName) && cityName.equals(chunkCity.get(e.getKey())))
                cityChunks.add(e.getKey());
        }

        // Собираем TaxBlocks из чанков города
        Set<Long> cityChunkLongs = new HashSet<>();
        for (ChunkPos cp : cityChunks) cityChunkLongs.add(cp.toLong());

        List<Long> transferTax = new ArrayList<>();
        old.taxBlocks.removeIf(posLong -> {
            long cpLong = new ChunkPos(BlockPos.of(posLong)).toLong();
            if (cityChunkLongs.contains(cpLong)) { transferTax.add(posLong); return true; }
            return false;
        });

        // Создаём новую страну
        Country newCountry = new Country(cityName, newLeaderUUID);
        newCountry.balance = old.cityBalances.getOrDefault(cityName, 0);
        newCountry.taxBlocks.addAll(transferTax);
        countries.put(cityName, newCountry);

        // Передаём чанки
        for (ChunkPos pos : cityChunks) {
            chunkOwners.put(pos, cityName);
            chunkCity.remove(pos);
            PacketDistributor.sendToAllPlayers(new SyncChunkPayload(pos, getColorForChunk(pos)));
        }

        // Очищаем записи в старой стране
        old.setRole(newLeaderUUID, CountryRole.GUEST);
        old.cities.remove(cityName);
        old.cityBalances.remove(cityName);
        old.cityMayors.remove(cityName);
        old.cityColors.remove(cityName);

        setDirty();
        return true;
    }

    // ── ГОРОДА ────────────────────────────────────────────────────────────

    public void createCityEntry(String cn, String city) {
        Country c = countries.get(cn);
        if (c != null && !c.cities.contains(city)) c.cities.add(city);
    }
    public void createCity(String cn, String city)   { createCityEntry(cn, city); setDirty(); }
    public void setCapital(String cn, String city)   {
        Country c = countries.get(cn);
        if (c != null && c.cities.contains(city)) { c.capital = city; c.cityColors.put(city, 0xFFD700); setDirty(); }
    }

    // ── УДАЛЕНИЕ СТРАНЫ ───────────────────────────────────────────────────

    public boolean deleteCountry(String name) {
        if (!countries.containsKey(name)) return false;
        List<ChunkPos> toRemove = new ArrayList<>();
        for (Map.Entry<ChunkPos, String> e : chunkOwners.entrySet())
            if (e.getValue().equals(name)) toRemove.add(e.getKey());
        for (ChunkPos pos : toRemove) {
            chunkOwners.remove(pos); chunkCity.remove(pos);
            PacketDistributor.sendToAllPlayers(new SyncChunkPayload(pos, 0));
        }
        diplomacy.remove(name);
        for (Map<String, DiplomacyStatus> rel : diplomacy.values()) rel.remove(name);
        warCooldowns.remove(name);
        tributes.remove(name);
        tributes.entrySet().removeIf(e -> e.getValue()[0].equals(name));
        countries.remove(name);
        setDirty();
        return true;
    }

    public boolean renameCountry(String old, String nw)        { return false; }
    public boolean renameCity(String c, String old, String nw) { return false; }
    public void collectTaxes(ServerLevel level)                {}
}

# 🏛️ PoliticsMod

**Build Nations. Manage Economies. Rule the World.**

<center><img src="https://i.imgur.com/dfT7DMH.png" alt="PoliticsMod Banner" width="500px"></center>

> ⚠️ **EARLY DEVELOPMENT WARNING**
>
> This mod is currently in **Early Access (v0.3)**. Features are subject to change and bugs may occur.
>
> * **Loaders:** NeoForge & Fabric
> * **Minecraft Version:** 1.21.1
> * **Game Mode:** Designed for **Creative Mode** & **Roleplay (RP) Servers**. The Trade Warehouse also works in **Survival**.

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[GeckoLib](https://modrinth.com/mod/geckolib)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

---

## 📖 About

**PoliticsMod** brings a full political and economic simulation layer to Minecraft. Unlike simple land-claim mods, PoliticsMod models the inner life of a nation — founding a capital, governing cities, managing a treasury, trading with other nations, conducting diplomacy, and watching your borders.

Whether you are running a small kingdom or a massive empire on an RP server, this mod gives you the tools to secure territory, manage citizens, and simulate a living country.

---

## ✨ Features

### 🏳️ State Management
- **Found Your Nation** — Place the **Founding Stone**, right-click it, and enter your country name. One nation per player.
- **Dashboard** — Press `K` to open the management screen with five tabs: **Overview**, **Map**, **Citizens**, **Diplomacy** and **Economy**.
- **Custom Flag** — The leader pastes a direct PNG link on the Overview tab; the flag is shown in the dashboard.
- **Roles** — **Leader**, **Mayor**, **Citizen**. Leaders appoint mayors from the Citizens tab or with `/politicsmod setrole`.

### 🏙️ Cities & Territory
- **City Stones** — Place a City Stone to found a city within your territory.
- **Chunk Claiming** — Select chunks on the Map tab and assign them to your country or a city, or use `/politicsmod claim`.
- **Borders** — Territory borders are drawn in the world. Toggle them with `/politicsmod borders` and switch style (Lines, Walls, Corners) with `/politicsmod borders style`.
- **Territory Trading** — Stand in another nation's chunk and offer a price; their leader accepts or rejects the offer standing on that chunk.

### 👥 Citizens
- Invite, kick and promote players from the **Citizens** tab (leaders and mayors).
- Kicking and changing roles requires the player to be online.

### 💰 Economy
- **Treasury** — Every nation has a balance, shown in the HUD and the **Economy** tab.
- **Tax Blocks** — Place up to 4 per chunk on your territory to earn income every economy cycle.
- **Federal Tax** — Tax blocks inside cities fill the city treasury; the leader sets the federal tax share that goes to the nation.
- **Upkeep** — Each claimed chunk costs upkeep every cycle.
- **Vault** — Each Vault raises the treasury limit. Right-click it to see the current limit.
- **Residential Buildings** — Simulate population based on housing near the block.

### 🛒 Trade Warehouse — Market
- Build a **Trade Warehouse** on your territory to access the **global market between nations**.
- **Sell:** any citizen lists the item in their hand for a price.
- **Buy:** leaders and mayors buy other nations' goods with the treasury.
- **Embargo:** nations at war cannot trade. **Allies** trade without the market fee.
- Works in **Survival**: crafted from a chest, gold ingots and planks.

### 🤝 Diplomacy
- Declare **war**, propose an **alliance** or **peace**, and accept or reject proposals from the **Diplomacy** tab.
- Declaring war costs money and has a cooldown.
- **Surrender** ends a war with a tribute: the losing nation pays a share of its income every cycle.
- Allied players cannot attack each other.
- **Secession:** if the federal tax is too high, a mayor can declare their city independent.

### 🏦 Embassy
- Place an **Embassy** on your territory and link it to another nation with `/politicsmod embassy link <country>` while standing in its chunk.
- Each linked embassy gives an **income bonus** every cycle, suspended while the two nations are at war.

### 📡 Radar
- Place a **Radar** on your territory.
- When players from a nation you are **at war** with enter its range, all your online citizens get a **HUD alert**.

### 🛡️ Protection
- Outsiders in Survival cannot break or interact with blocks inside your territory.
- During a war, enemies can break blocks in your **border chunks**.

### 📊 HUD
- Current country and city, your nation's treasury, and active radar alerts.

---

## 🚀 Quick Start

1. Switch to **Creative Mode** and open the **PoliticsMod** tab in your inventory.
2. Place the **Founding Stone** and right-click it — enter your country name.
3. Place a **City Stone** and name your first city.
4. Press `K` → **Map** tab → select chunks and claim them.
5. Place **Tax Blocks** (up to 4 per chunk) to start earning income.
6. Invite players on the **Citizens** tab.
7. Build a **Trade Warehouse** and start trading with other nations.
8. Link an **Embassy** to a partner nation and place a **Radar** to watch your borders.

---

## 🔧 Commands Reference

Most actions are also available in the dashboard (`K`).

### Nation & Citizens

| Command | Who | Description |
|---|---|---|
| `/politicsmod info` | Member | Nation summary: role, treasury, income and upkeep |
| `/politicsmod invite <player>` | Leader / Mayor | Add an online player as a citizen |
| `/politicsmod kick <player>` | Leader / Mayor | Remove a citizen |
| `/politicsmod setrole <player> <mayor\|citizen>` | Leader | Change a citizen's role |
| `/politicsmod claim [city]` | Leader / Mayor | Claim the current chunk for the nation or a city |
| `/politicsmod claim_radius <radius> [city]` | Leader / Mayor | Claim chunks around you |
| `/politicsmod pay <country> <amount>` | Leader | Transfer money to another nation |

### Cities & Taxes

| Command | Who | Description |
|---|---|---|
| `/politicsmod tax federal <0-99>` | Leader | Set the federal tax rate |
| `/politicsmod tax info` | Member | Tax overview |
| `/politicsmod city setmayor <city> <player>` | Leader | Appoint a city mayor |
| `/politicsmod city info [city]` | Member | City treasury and mayor |
| `/politicsmod city secede` | Mayor | Declare your city independent (needs a high federal tax) |

### Diplomacy

| Command | Who | Description |
|---|---|---|
| `/politicsmod diplomacy war <country>` | Leader | Declare war |
| `/politicsmod diplomacy alliance <country>` | Leader | Propose an alliance |
| `/politicsmod diplomacy peace <country>` | Leader | Propose peace |
| `/politicsmod diplomacy surrender <country>` | Leader | Surrender and start paying tribute |
| `/politicsmod diplomacy accept` / `reject` | Leader | Answer an incoming proposal |
| `/politicsmod diplomacy status [country]` | Member | Show relations |
| `/politicsmod diplomacy tribute` | Member | Show tribute obligations |
| `/politicsmod diplomacy tribute cancel <country>` | Leader | Release a nation from paying you tribute |

### Territory & Embassies

| Command | Who | Description |
|---|---|---|
| `/politicsmod territory offer <price>` | Leader / Mayor | Offer to buy the chunk you stand in |
| `/politicsmod territory accept` / `reject` | Leader (owner) | Answer the offer for the chunk you stand in |
| `/politicsmod territory offers` | Leader | List incoming purchase offers |
| `/politicsmod embassy link <country>` | Leader / Mayor | Link the embassy in your chunk |
| `/politicsmod embassy unlink` | Member | Unlink the embassy in your chunk |
| `/politicsmod embassy info` | Leader | List your embassies |

### Client & Admin

| Command | Who | Description |
|---|---|---|
| `/politicsmod borders` | Anyone | Toggle border rendering |
| `/politicsmod borders style` | Anyone | Switch border style |
| `/politicsmod reload` | Operator | Reload `config/politicsmod.json` |

---

## ⚙️ Configuration

Server settings live in `config/politicsmod.json` and are created on first launch. Change values and run `/politicsmod reload`.

| Group | Settings |
|---|---|
| Territory | `countryChunkPrice`, `cityChunkPrice`, `upkeepPerChunk` |
| Economy | `economyCycleTicks`, `incomePerTaxBlock`, `maxTaxBlocksPerChunk`, `baseMaxBalance`, `vaultBalanceBonus`, `embassyIncomeBonus` |
| Diplomacy | `warCost`, `warCooldownMinutes`, `tributeRateDefault`, `secedeThresholdPercent` |
| Market | `marketFeePercent`, `maxListingsPerCountry`, `maxListingPrice` |
| Radar | `radarRangeChunks`, `radarCheckTicks` |

---

## 📦 Blocks

| Block | Description |
|---|---|
| Founding Stone | Founds and manages your nation |
| City Stone | Founds a city |
| Residential Building | Simulates housing & population |
| Tax Block | Passive income every economy cycle (max 4 per chunk) |
| Embassy | Income bonus from a linked partner nation |
| Radar | Alerts citizens when enemies at war come near |
| Vault | Raises the treasury limit |
| Trade Warehouse | Access to the market between nations (works in Survival) |

---

## 🌍 Localization

Fully localized in seven languages — all in-game messages, HUD and GUI labels follow the player's language setting:

| Language | Code |
|---|---|
| English | `en_us` |
| Russian | `ru_ru` |
| Ukrainian | `uk_ua` |
| Polish | `pl_pl` |
| German | `de_de` |
| French | `fr_fr` |
| Spanish | `es_es` |

---

## ⚖️ License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

- **Modpacks:** Free to include in any modpack.
- **Source Code:** Available on [GitHub](https://github.com/krona04/politics-mod). Free to view, modify, and distribute — any derivative work must also be released under GPL-3.0.
- **Distribution:** Redistribution permitted provided the source code is made available and the original license file is included.

---

## 🐛 Bug Reports & Suggestions

Found a bug or have a feature idea?  
Please open an issue on the **[GitHub Issues Page](https://github.com/krona04/politics-mod/issues)**.

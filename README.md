# 🏛️ PoliticsMod

**Build Nations. Manage Economies. Rule the World.**

<center><img src="https://i.imgur.com/dfT7DMH.png" alt="PoliticsMod Banner" width="500px"></center>

> ⚠️ **EARLY DEVELOPMENT WARNING**
>
> This mod is currently in **Early Access (v0.4)**. Features are subject to change and bugs may occur.
>
> * **Loaders:** NeoForge, Forge (1.20.1) & Fabric
> * **Minecraft Version:** 1.21.1 & 1.20.1
> * **Game Mode:** **Survival** and **Creative**, built for **Roleplay (RP) Servers**.

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
- **Found Your Nation** — Craft and place the **Founding Stone**, right-click it, and enter your country name. The 5×5 chunks around it must be unclaimed. One nation per player.
- **Dissolve** — `/politicsmod delete`, then `/politicsmod confirm` within 30 seconds.
- **Dashboard** — Press `K` to open the management screen with five tabs: **Overview**, **Map**, **Citizens**, **Diplomacy** and **Economy**.
- **Custom Flag** — The leader pastes a direct PNG link on the Overview tab; the flag is shown in the dashboard.
- **Roles** — **Leader**, **Mayor**, **Citizen**. Leaders appoint mayors from the Citizens tab or with `/politicsmod setrole`.

### 🏙️ Cities & Territory
- **City Stones** — Place a City Stone to found a city within your territory.
- **Chunk Claiming** — Select chunks on the Map tab and assign them to your country or a city, or use `/politicsmod claim`.
- **Borders** — Territory borders are drawn in the world. Toggle them with `/politicsmod borders` and switch style (line on the ground, glowing fence, dashed line) with `/politicsmod borders style`. Borders follow the terrain and are coloured by relation: yours green, allies blue, enemies red.
- **Territory Trading** — Stand in another nation's chunk and offer a price; their leader accepts or rejects the offer standing on that chunk.

### 👥 Citizens
- Invite, kick and promote players from the **Citizens** tab (leaders and mayors).
- Kicking and changing roles requires the player to be online.
- What mayors and citizens may do is configurable per server (see Configuration).

### 👛 Wallet & Salaries
- Every player has a personal **wallet**: `/politicsmod wallet` shows the balance and history, `send` pays another player, `donate` gives to your treasury.
- The leader pays **salaries** from the treasury every economy cycle (Citizens tab or `/politicsmod salary set`).
- **Income tax** is taken from salaries and personal sales and returns to the treasury.
- **Currency exchange:** right-click a Vault holding emeralds (configurable) to turn them into money, or `/politicsmod wallet exchange`.

### 🏘️ Plots
- Mayors turn city chunks into **plots** and sell or rent them: `/politicsmod plot create`, `/politicsmod plot price sale|rent <amount>`.
- Citizens stand on a plot and `/politicsmod plot buy` or `/politicsmod plot rent`.
- Only the owner and trusted players can build or open containers there: `/politicsmod plot trust <player> [access|build]`. `lock` keeps strangers out.
- Rent is charged every cycle; a tenant who can't pay loses the plot.

### 💰 Economy
- **Treasury** — Every nation has a balance, shown in the HUD and the **Economy** tab.
- **Tax Blocks** — Place up to 4 per chunk on your territory to earn income every economy cycle.
- **Federal Tax** — Tax blocks inside cities fill the city treasury; the leader sets the federal tax share that goes to the nation.
- **Upkeep** — Each claimed chunk costs upkeep every cycle.
- **Vault** — Each Vault raises the treasury limit. Right-click it to see the current limit.
- **Residential Buildings** — Simulate population based on housing near the block.

### 🛒 Trade Warehouse — Market
- Build a **Trade Warehouse** on your territory to access the **global market** of all nations.
- **Sell:** list the item in your hand for your own wallet, or on behalf of the state for the treasury.
- **Buy:** pay from your wallet, or from the treasury (leaders and mayors).
- **Embargo:** nations at war cannot trade. Trade inside a country and between **allies** has no fee.

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

### 🗳️ Elections & Laws
- The leader calls elections for leader or mayor; citizens run and vote. A petition of half the citizens forces a leader election.
- Laws: **Closed Borders**, **Martial Law**, **Free Trade** — on the Politics tab or with `/politicsmod law`.

### ⚔️ Sieges
- During a war, start a siege in an enemy border chunk and hold it with more fighters than the defenders until the bar fills — the chunk becomes yours.

### 💸 Debt & Loans
- Treasuries can go into debt. Bankrupt countries can't wage war, and their mayors may secede.
- Allies lend money with interest, repaid every economy cycle.

### 📰 News & Maps
- A news feed of everything that happens between nations (`/politicsmod news`, News tab).
- **Flagpole** block that flies the flag of the land's owner.
- Country borders on **BlueMap**, **Dynmap** and **JourneyMap** when they are installed.

### 🛡️ Protection
- Outsiders cannot break or interact with blocks inside your territory. Only operators bypass protection.
- During a war, enemies can break blocks only in a chunk they are **besieging**.
- **Protection flags** for the whole country or a single city: PvP, explosions, mob explosions, fire spread, item pickup and entry of strangers. Set them on the **Protection** screen (Map tab) or with `/politicsmod flag`.

### 📖 Guide
- A 16-page **guide book** in your own language, given on first join. Craft another from a book and a gold nugget, or use `/politicsmod guide`.

### 📊 HUD
- Current country, city and plot, your nation's treasury, your wallet, and active radar alerts.

---

## 🚀 Quick Start

1. Craft a **Founding Stone** (or take it from the **PoliticsMod** creative tab).
2. Place it and right-click it — enter your country name.
3. Place a **City Stone** and name your first city.
4. Press `K` → **Map** tab → select chunks and claim them.
5. Place **Tax Blocks** (up to 4 per chunk) to start earning income.
6. Invite players on the **Citizens** tab.
7. Build a **Trade Warehouse** and start trading with other nations.
8. Pay salaries, sell plots to your citizens and set up land protection.
9. Read the guide book for everything else.

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
| `/politicsmod delete` / `confirm` | Leader | Dissolve your nation |

### Wallet & Salaries

| Command | Who | Description |
|---|---|---|
| `/politicsmod wallet` / `wallet history` | Anyone | Your balance and operations |
| `/politicsmod wallet send <player> <amount>` | Anyone | Pay another player |
| `/politicsmod wallet donate <amount>` | Member | Give money to your treasury |
| `/politicsmod wallet exchange [count]` | Anyone | Turn the held exchange item into money |
| `/politicsmod wallet withdraw <count>` | Anyone | Turn money back into items (off by default) |
| `/politicsmod salary set <player> <amount>` | Leader | Set a salary (0 removes it) |
| `/politicsmod salary list` | Member | Salaries of your country |
| `/politicsmod tax income <rate>` | Leader | Set the income tax |

### Plots

| Command | Who | Description |
|---|---|---|
| `/politicsmod plot` / `plot list` | Anyone | Plot you stand on / plots of your country |
| `/politicsmod plot create` / `delete` | Leader / Mayor | Turn the city chunk you stand in into a plot, or remove it |
| `/politicsmod plot price sale\|rent <amount>` | Leader / Mayor | Set the price (0 disables) |
| `/politicsmod plot evict` | Leader / Mayor | Free the plot |
| `/politicsmod plot buy` / `rent` | Citizen | Get the plot you stand on |
| `/politicsmod plot trust <player> [access\|build]` / `untrust <player>` | Owner | Share your plot |
| `/politicsmod plot lock` / `unlock` / `leave` | Owner | Keep strangers out / let them in / give the plot up |

### Politics & War

| Command | Who | Description |
|---|---|---|
| `/politicsmod election` | Member | Current election and petition |
| `/politicsmod election start leader` / `start mayor <city>` | Leader | Call an election |
| `/politicsmod election run` / `vote <name>` | Member | Stand as a candidate / vote |
| `/politicsmod election petition` | Citizen | Sign the petition for a new leader |
| `/politicsmod law` / `law enact\|repeal <law>` | Member / Leader | Laws: `closed_borders`, `martial_law`, `free_trade` |
| `/politicsmod siege start` | Leader / Mayor | Besiege the enemy border chunk you stand in |
| `/politicsmod siege status` / `cancel` | Member / Leader | Running sieges / call them off |
| `/politicsmod loan offer <amount> <interest> <cycles> <country>` | Leader | Offer a loan to an ally |
| `/politicsmod loan accept\|reject\|list` | Leader / Member | Answer an offer, see your loans |
| `/politicsmod news [count]` | Anyone | Latest news |

### Protection

| Command | Who | Description |
|---|---|---|
| `/politicsmod flag` | Anyone | Protection where you stand |
| `/politicsmod flag set <flag> <on\|off\|default>` | Leader | Protection for the whole country |
| `/politicsmod flag city <city> <flag> <on\|off\|default>` | Leader / city mayor | Protection for one city |

Flags: `pvp`, `explosions`, `mob_explosions`, `fire_spread`, `item_pickup`, `entry`.

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
| `/politicsmod embassy unlink` | Leader / Mayor | Unlink the embassy in your chunk |
| `/politicsmod embassy info` | Leader | List your embassies |

### Client & Admin

| Command | Who | Description |
|---|---|---|
| `/politicsmod help` | Anyone | Command overview |
| `/politicsmod guide` | Anyone | Get the guide book |
| `/politicsmod borders` | Anyone | Toggle border rendering |
| `/politicsmod borders style` | Anyone | Switch border style |
| `/politicsmod reload` | Operator | Reload `config/politicsmod.json` |
| `/politicsmod wallet give` / `take <player> <amount>` | Operator | Adjust a wallet |

### Permissions

Every command group has a permission node `politicsmod.command.<group>` for LuckPerms and similar mods:
`info`, `members`, `claim`, `rename`, `pay`, `tax`, `city`, `territory`, `embassy`, `diplomacy`, `wallet`, `salary`, `plot`, `flag`, `borders`, `delete`, `guide`, `help`, `election`, `law`, `siege`, `loan`, `news` (everyone by default), `reload` and `admin` (operators by default).

---

## ⚙️ Configuration

Server settings live in `config/politicsmod.json` and are created on first launch. Change values and run `/politicsmod reload`.

| Group | Settings |
|---|---|
| Territory | `countryChunkPrice`, `cityChunkPrice`, `upkeepPerChunk` |
| Economy | `economyCycleTicks`, `incomePerTaxBlock`, `maxTaxBlocksPerChunk`, `baseMaxBalance`, `vaultBalanceBonus`, `embassyIncomeBonus` |
| Diplomacy | `warCost`, `warCooldownMinutes`, `tributeRateDefault`, `secedeThresholdPercent` |
| Market | `marketFeePercent`, `maxListingsPerCountry`, `maxListingsPerPlayer`, `maxListingPrice` |
| Wallets | `walletStartBalance`, `walletMaxBalance`, `walletHistorySize`, `maxSalary`, `defaultIncomeTaxPercent`, `maxIncomeTaxPercent` |
| Exchange | `exchangeEnabled`, `exchangeItem`, `exchangeRate`, `exchangeWithdrawEnabled` |
| Protection | `creativeBypassesProtection`, `flagDefaultPvp`, `flagDefaultExplosions`, `flagDefaultMobExplosions`, `flagDefaultFireSpread`, `flagDefaultItemPickup`, `flagDefaultEntry` |
| Plots | `maxPlotsPerPlayer` |
| Roles | `rolePermissions` — actions allowed for `MAYOR` and `CITIZEN`: `INVITE`, `KICK`, `CLAIM_CITY`, `MANAGE_EMBASSIES`, `SPEND_TREASURY`, `BUY_TERRITORY`, `MANAGE_PLOTS`, `SELL_FOR_STATE` |
| Elections | `electionDurationMinutes`, `electionCooldownMinutes`, `petitionPercent` |
| Laws | `lawChangeCooldownMinutes`, `martialLawIncomePenaltyPercent`, `martialLawSiegeMultiplier`, `freeTradeEmbassyBonusPercent` |
| Sieges | `siegeCost`, `siegeHoldSeconds`, `siegeGraceSeconds`, `maxSiegesPerCountry` |
| Debt | `maxDebt`, `maxLoanInterestPercent`, `maxLoanCycles` |
| News & maps | `newsMaxEntries`, `webMapsEnabled`, `webMapUpdateSeconds` |
| Guide | `giveGuideOnFirstJoin` |
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
| Trade Warehouse | Access to the market of all nations |
| Flagpole | Flies the flag of the country that owns the land |

Every block can be crafted and drops when broken with the right tool. The **PoliticsMod Guide** book is crafted from a book and a gold nugget.

---

## 🌍 Localization

Fully localized in twelve languages — all in-game messages, HUD and GUI labels follow the player's language setting:

| Language | Code |
|---|---|
| English | `en_us` |
| Russian | `ru_ru` |
| Ukrainian | `uk_ua` |
| Polish | `pl_pl` |
| German | `de_de` |
| French | `fr_fr` |
| Spanish | `es_es` |
| Portuguese (Brazil) | `pt_br` |
| Portuguese (Portugal) | `pt_pt` |
| Italian | `it_it` |
| Chinese (Simplified) | `zh_cn` |
| Japanese | `ja_jp` |

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

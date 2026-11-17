# Events, Decisions & Goals in Datapacks

Since 0.5.1 every event, decision and goal of PoliticsMod is a JSON file. A server can add its own, change the built-in
ones or switch them off with an ordinary datapack. `/reload` loads the changes.

```
data/<namespace>/politicsmod/events/<name>.json
data/<namespace>/politicsmod/decisions/<name>.json
data/<namespace>/politicsmod/goals/<name>.json
```

The id is `<namespace>:<name>`, for example `politicsmod:good_harvest`. The built-in files are in the mod jar under
`data/politicsmod/politicsmod/`. To change one, put a file with the same path into your datapack. To switch one off,
put `{"enabled": false}` there.

## Texts

Titles, descriptions and option texts are **translation keys**. If a file gives none, these keys are used:

| What | Key |
|---|---|
| Event | `event.<namespace>.<name>.title`, `.desc`, `.option.1`, `.option.2`, … |
| Decision | `decision.<namespace>.<name>.title`, `.desc` |
| Goal | `goal.<namespace>.<name>.title`, `.desc` |

Put the texts in a resource pack, or write plain text instead of a key: `"title": "The King's Hunt"`. An unknown key is
shown as it is written.

Descriptions may use `%1$s` (the country) and `%2$s` (the city of a city event). Titles and option texts should not,
because the news feed shows them without these values.

## Events

```json
{
  "mean_time": 90,
  "cooldown": 120,
  "conditions": [{"type": "cities", "min": 1}],
  "options": [
    {"effects": [{"type": "treasury", "amount": 400}]},
    {"effects": [{"type": "stability", "amount": 20}]}
  ]
}
```

| Field | Meaning | Default |
|---|---|---|
| `mean_time` | Average number of economy cycles until the event happens while its conditions hold (1 cycle = 3 min by default) | 60 |
| `cooldown` | Cycles before the same event can happen again | 120 |
| `once` | Happens only once per country | false |
| `conditions` | When the event can happen | always |
| `city` | The event happens in one city, chosen among those that pass `city_conditions` | false |
| `city_conditions` | Requirements for that city (`city_unrest`, `city_villagers`, `is_capital`, …) | any city |
| `timeout` | Cycles the leader has to answer | `eventTimeoutCycles` from the config |
| `default_option` | Choice (1, 2, …) that happens when nobody answers | the last one |
| `options` | Two or more choices: `effects`, optional `conditions` (the choice is greyed out when they fail) and `text` | — |

Events happen only while a member of the country is online (`eventsNeedOnlineMember`), at most `maxPendingEvents`
wait at a time, and at least `eventMinGapCycles` pass between two. `eventFrequencyPercent` scales how often they come.
The leader answers. While the leader is offline, a mayor may answer.

## Decisions

```json
{
  "order": 30,
  "cost": 1500,
  "potential": [{"type": "cities", "min": 2}],
  "conditions": [{"type": "stability", "min": 0}],
  "target_city": true,
  "city_conditions": [{"type": "is_capital", "value": false}],
  "cooldown": 200,
  "effects": [{"type": "capital"}, {"type": "stability", "amount": -20}]
}
```

| Field | Meaning | Default |
|---|---|---|
| `order` | Position in the list | 100 |
| `potential` | When the decision is shown at all | always |
| `conditions` | When it can be taken (shown with ✔/✘) | always |
| `cost` | Treasury money it takes | 0 |
| `target_city` | The leader picks one of the cities (passing `city_conditions`); city effects act on it | false |
| `once` | Can be taken only once | false |
| `cooldown` | Cycles before it can be taken again | 0 |
| `effects` | What it does | — |

## Goals

```json
{
  "order": 90,
  "start": [{"type": "debt", "min": 1}],
  "conditions": [{"type": "debt", "max": 0}],
  "reward": [{"type": "stability", "amount": 20}]
}
```

A goal starts the first time `start` holds (always, if there is no `start`) and is reached when `conditions` hold.
Then `reward` happens once. The dashboard shows the progress of open goals.

## Conditions

A list means "all of these". Numbers take `min`, `max` or `value`:

| Type | Value |
|---|---|
| `stability` | −3…+3 |
| `legitimacy`, `prestige` | 0…100, −100…100 |
| `treasury`, `debt` | $ |
| `chunks`, `cities`, `citizens` (players), `villagers`, `population` (both), `tax_blocks` | count |
| `development` | sum over all cities |
| `max_unrest` | unrest of the most restless city |
| `wars`, `allies`, `embassies` (active) | count |
| `overextension` | % of land that is not yet core |
| `federal_tax` | % |
| `rank` | 0 principality, 1 kingdom, 2 empire |
| `petition` | signatures |
| `villager_mayors` | cities with a villager mayor |
| `country_age` | cycles since the country was founded (hidden) |
| `city_unrest`, `city_development`, `city_villagers`, `city_chunks` | the event's or decision's city (else the capital) |

Others:

| Type | Fields |
|---|---|
| `law` | `law`: `closed_borders`, `martial_law`, `free_trade`; `value`: true/false |
| `bankrupt`, `election`, `is_capital`, `villager_mayor` | `value`: true/false |
| `flag` | `flag`, `value` (flags set by effects; never shown) |
| `goal_done`, `decision_taken` | `goal` / `decision` id, `value` |
| `election_ready` | no election running and the cooldown is over (hidden) |
| `any`, `all`, `not` | `conditions`: a list |

Any condition takes `"hidden": true` to keep it out of the explanations.

## Effects

| Type | Fields | What it does |
|---|---|---|
| `treasury` | `amount` and/or `percent` | Money in or out of the treasury; it may go into debt down to `maxDebt` |
| `city_treasury` | `amount` | The city's treasury |
| `stability` | `amount` | Stability progress (100 = one level by default) |
| `legitimacy`, `prestige` | `amount` | Points |
| `modifier` | `modifier`, `value`, `cycles` | A country modifier: `tax_income`, `embassy_income`, `upkeep`, `land_price`, `siege_defense` (%), `unrest` (points in every city); `cycles` 0 = for good |
| `city_modifier` | `modifier` (`unrest` or `tax_income`), `value`, `cycles` | The same for one city |
| `development` | `amount` | Development levels of the city |
| `wallets` | `amount` | Money to (or from) the wallet of every citizen |
| `rank` | `rank` | 0, 1, 2; each rank above principality gives +5% tax and +10% embassy income |
| `capital` | — | Moves the capital to the decision's city |
| `election` | — | Starts a leader election |
| `set_flag`, `clear_flag` | `flag`, `cycles` | Flags for later conditions |
| `news` | `key` | A line in the news feed (`%1$s` country, `%2$s` city) |

Effects without a city of their own (development, city modifiers, city treasury) act on the event's or decision's
city, else on the capital.

## Testing

`/politicsmod event fire <id> <country>` (operators) starts an event at once. A file with a mistake is skipped. The
server log says which file and why: `Skipping event mypack:storm: Unknown effect type 'tresury'`.

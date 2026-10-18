# GE Profit

Market analytics for the Old School RuneScape Grand Exchange. **One Spring Boot backend, two clients** — a React dashboard and a RuneLite plugin that shows the same numbers inside the game.

It answers one question: *given live prices, what is actually worth trading right now?* — with the emphasis on **actually**, because the naive version of this tool produces a list of trades nobody can fill.

---

## The three calculators

**Flipping** — buy at the low price, sell at the high one, keep the difference less tax.

**Decanting** — potions come in 1, 2, 3 and 4-dose forms of the same drink, and decanting moves freely between them, so every form should cost the same *per dose*. Often they don't, because players buy the convenient 4-dose and dump the leftovers.

**Processing** — high alchemy, plus a recipe calculator that values a recipe's inputs against what its output sells for.

## The decisions worth defending

**Ranking by potential profit, not margin.** A 2,000gp margin on an item you may buy 8 of per four hours is worth 16,000gp. A 57gp margin on one you may buy 13,000 of is worth 741,000gp. Sorting by raw margin — which is what most flip finders do — puts the wrong item first. The default sort is `margin × buy limit`.

**Volume filtering.** A huge margin on an item that trades twice a day is a price gap nobody is crossing, not an opportunity. Both legs of a decant must be liquid too: a trade you can enter but not exit is worse than no trade.

**Working in doses.** Comparing potion prices directly is invalid — the forms are different quantities of the same thing. Everything in the decanter reduces to price per dose first.

**Tax comes off the sell side, and alchemy is exempt.** High alchemy sells to the game rather than to another player, so no GE tax applies. Missing that makes alching look worse than it is.

**Why a backend at all,** rather than each client calling the Wiki directly:

- The margin rules live in **one tested place** instead of being duplicated between the plugin and the website and drifting apart.
- The server keeps **price history**, so it can answer "is this cheap compared with the last month?". The Wiki API only serves recent windows, and a game client is the wrong place to accumulate weeks of data.
- The Wiki sees **one polite poller** no matter how many dashboards and plugins are open.

## Architecture

```
   OSRS Wiki prices API
            │  /mapping daily, /latest every minute, /5m archived as history
            ▼
   ┌──────────────────────┐        ┌─────────────────────────┐
   │  Spring Boot backend │───────▶│ Postgres                │
   │  scheduled ingestion │        │  items                  │
   │  flip / decant /     │        │  latest_prices  (now)   │
   │  processing          │        │  price_history  (before)│
   └──────────┬───────────┘        └─────────────────────────┘
              │  /api/flips  /api/decant  /api/alch  /api/recipes  /api/items
      ┌───────┴────────┐
      ▼                ▼
  React dashboard   RuneLite plugin
  (Vite, Tailwind)  (Java, in-game panel)
```

## Tech, and where each piece lives

| | Where |
|---|---|
| Java 21, Spring Boot 3.3 | `backend/` |
| Spring Data JPA, Postgres | `backend/src/main/java/com/geprofit/domain`, `repo` |
| Flyway migrations | `backend/src/main/resources/db/migration` |
| `@Scheduled` ingestion | `backend/.../ingest/PriceIngestionService.java` |
| REST API | `backend/.../web` |
| JUnit 5 + Testcontainers | `backend/src/test` |
| React 18, TypeScript, Vite, Tailwind | `web/` |
| RuneLite plugin | `plugin/` |
| Gradle (both Java modules) | `backend/build.gradle`, `plugin/build.gradle` |

## Running it

**You need JDK 21** (Gradle does not run on newer JDKs yet, and Spring Boot 3.3 targets 17–21):

```bash
sdk install java 21.0.12+1.1-tem
```

**The database:**

```bash
docker compose up -d db
```

**The backend** (Flyway creates the schema on first start, then ingestion begins):

```bash
cd backend
./gradlew bootRun
# http://localhost:8080/api/flips
```

**The dashboard:**

```bash
cd web
npm install
npm run dev
# http://localhost:5173
```

**The plugin:**

```bash
cd plugin
./gradlew run     # launches a RuneLite client with the plugin loaded
```

Then enable **Fetch live prices** in the plugin settings — network access is off until you do. To read from your own backend instead, enable **Use the GE Profit backend** and set the URL.

## Tests

```bash
cd backend && ./gradlew test     # 47 tests
cd web && npm test               # 9 tests
cd plugin && ./gradlew test      # 4 tests: the plugin's own tax and price checks
```

The backend's unit tests mock the repository and need no services. The integration tests use **Testcontainers with real Postgres**, because the things they verify — the Flyway migration, the foreign keys, and the unique index that makes archiving idempotent — only exist in Postgres. An in-memory database would pass while the real schema was broken.

## The bug that froze the prices

For a week the dashboard showed the same prices, with every test passing. On 2026-10-01 the Wiki API reported one item at 9,100,000,000 gp. The response was parsed into `Integer` fields, Jackson refused a number that size, and the whole batch of about 4,000 prices was thrown away, every five minutes. The only visible sign was the "Priced" column saying "7 days ago".

The fix reads prices as `Long` and turns anything the game itself can't hold (above `Integer.MAX_VALUE`, or negative) into "no price" for that one item, in `WikiPricesClient.gp()`. Two lessons stayed in the tests: `WikiPricesParsingTest` parses the real response shape, because the ingestion tests mock the client and so never parsed anything; and one item with an impossible price must not stop everyone else's from being stored.

The plugin's direct-to-Wiki mode had the same `Integer` fields and the same failure, shown as "Couldn't read the price data". It now reads `Long` and skips that one item too (`GePricesService.fitsInGame`), and it ranks by a full buy limit's profit, the same order as the backend.

## A note on the price API

Prices come from the [OSRS Wiki real-time prices API](https://prices.runescape.wiki/), which is free and run by volunteers. This project identifies itself with a descriptive User-Agent and polls no faster than the data changes. If you run your own copy, **put your own contact in `geprofit.wiki.user-agent`**.

## Honest limitations

- **The GE tax rate is configuration, not a verified constant.** The rate, the cap and the exemption have all changed during OSRS's lifetime. Check `geprofit.tax` in `application.yml` against the live game before trusting the numbers.
- **Averages need history.** The 1-day, 1-week and 4-week figures are computed from samples this server collected, so they are empty on a fresh install and fill in over time.
- **Recipes cover two families** — herb cleaning and unfinished potions — derived from name patterns rather than a hand-written list of every recipe in the game.
- **No accounts, no persistence of your trades.** It tells you what looks profitable; it does not track what you did.

Prices move constantly. Every number here is a starting point, not a promise.

## Learning the code

See **`CODE_TOUR.md`** for a guided read, file by file, with the decisions an interviewer would probe.

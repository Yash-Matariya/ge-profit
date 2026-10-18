# Code Tour — GE Profit

Read this with the code open. It walks the files in the order the design makes sense and flags what an interviewer will push on. By the end you can walk anyone through the whole system and defend every choice. Everything here is in the code — nothing is aspirational.

**Reading order:** `GeTax` → `GeProfitProperties` → the migration → `domain/` → `repo/` → `WikiPricesClient` → `PriceIngestionService` → `FlipService` → `DecantService` → `ProcessingService` → `web/` controllers → the tests → then `web/` (React) and `plugin/`.

---

## 1. `tax/GeTax.java` — start here, everything depends on it

Every profit number in the application comes through this class, so an error here would not look like a bug — it would look like slightly wrong trading advice, which is worse.

Three rules: a percentage of the sale price, **rounded down**, **capped** per item, and items **below a threshold are exempt**. It also exposes `netSell` (what lands in your pocket) and `margin` (buy → sell, after tax).

**Why the rate is configuration, not a constant:** the rate, the cap and the exemption have all changed during OSRS's lifetime. When Jagex changes them again this is a config edit, not a code change.

> **Probe — "how do you know the tax is right?"** → "I don't hard-code it. It's three settings in one class with its own test suite, so if the game changes it's a config edit. The tests pin the behaviour that's easy to get wrong: it floors rather than rounds, it caps, and a losing trade reports a negative margin rather than clamping to zero."

## 2. `config/GeProfitProperties.java` — every tunable, typed, in one place

A record tree bound from `application.yml` with `@ConfigurationProperties`. Nothing reads configuration by string key at runtime, and the settings are validated at startup.

> **Probe — "why not `@Value`?"** → "`@Value` scatters configuration across the codebase as strings. A typed record means one place to look, compile-time safety, and failure at startup rather than at the first request."

## 3. `db/migration/V1__init.sql` — the schema, and why it is shaped this way

Three tables, and the split between them is the design:

- **`items`** — the catalogue. The primary key is **the game's own item id**, not a surrogate: it's stable, it's what every API endpoint keys on, and inventing our own would mean a lookup table for nothing.
- **`latest_prices`** — one row per item, overwritten every poll. This table is **"now"**.
- **`price_history`** — append-only five-minute samples. This is **"before"**, and it exists because the Wiki API only serves recent windows. The 1-day/1-week/4-week averages can only come from data we kept ourselves.

Two indexes earn their place: `(item_id, captured_at DESC)` because every history query is "this item, this window, newest first"; and a **unique** index on `(item_id, captured_at)` that makes ingestion idempotent.

> **Probe — "why keep your own history when the API has a /24h endpoint?"** → "The API serves fixed recent windows. A four-week average isn't available from it at all, so it has to be accumulated. That's the main reason there's a database rather than a proxy."

## 4. `domain/` — the entities, and one naming trap

`Item`, `LatestPrice`, `PriceHistory`. `Item` has no generated id because the id comes from outside.

**The trap, in `LatestPrice`:** the API's `high` is the price you **sell** at and `low` is the price you **buy** at. It reads backwards. Getting it the wrong way round inverts every margin in the application, so the entity exposes `buyAt()` and `sellAt()` and the rest of the code never touches `high`/`low`.

> **Probe — "what was the subtlest bug risk here?"** → "The API's high/low naming is from the other side of the trade. I wrapped it in `buyAt()`/`sellAt()` so nothing downstream has to remember which is which."

## 5. `repo/` — one query instead of N+1

`LatestPriceRepository.findAllPriced()` joins items to prices and returns a **projection** (`PricedItem`), not entities. The flip finder reads every priced item in the game on each request; loading `Item` entities and then each one's price would be the N+1 problem over roughly four thousand rows.

`PriceHistoryRepository` averages **in the database**. The alternative is pulling thousands of rows over the wire to add them up in Java. Note it skips rows missing either side rather than counting them as zero — a missing price is unknown, not free, and counting it as zero would silently drag every average down.

> **Probe — "how would you speed this up?"** → "It's already one query with a projection. The next step would be caching the computed flip list for the poll interval, since the underlying data only changes once a minute."

## 6. `wiki/WikiPricesClient.java` — the only class that talks to the Wiki

Isolating it means ingestion can be tested with a fake, and that the two obligations this free, volunteer-run API places on callers are honoured in exactly one place: **identify yourself** with a descriptive User-Agent, and **poll politely**.

> **Probe — "how do you test against a third-party API?"** → "I don't. The client is mocked in tests. Hitting a live volunteer-run service from a test suite would be slow, flaky and rude. What I test is what I do with the data. Plus one test that parses a real-shaped response, added after the API sent a price too big for an int and the parse silently dropped every price for a week."

## 7. `ingest/PriceIngestionService.java` — the heart of the backend

Three jobs on three schedules, because the data changes at three rates: catalogue **daily**, current prices **every minute**, five-minute windows **archived as history**.

Four decisions to know cold:

- **Each job swallows its own failure.** A missed poll is a gap in the data; an exception escaping a `@Scheduled` method silently kills that job forever. The second is far worse.
- **The catalogue is upserted, never deleted and reinserted.** `price_history` has a foreign key to `items`, so a delete would cascade weeks of history away.
- **Prices for unknown items are skipped**, not force-inserted. The Wiki occasionally prices an item before it appears in the mapping. Inventing a catalogue row to satisfy the foreign key would put a nameless item in front of users.
- **Archiving is idempotent.** The window's own timestamp is the key, so a retry after a partial failure writes nothing and cannot double-count volume into the averages.

> **Probe — "what happens if a poll fails?"** → "It's logged and swallowed, so the scheduler survives; the next poll fills in. Ingestion is idempotent, so a retry can't corrupt anything — that's enforced by a unique index, not by hoping."

## 8. `flip/FlipService.java` — the ranking argument

The arithmetic is easy. What makes it useful is the filtering, and the headline decision is ranking by **potential profit** (`margin × buy limit`) rather than raw margin. Sorting by margin alone is the most common mistake in tools like this, and there's a test that fails if someone "fixes" it back.

An item with no published buy limit counts as **one unit** — the honest assumption, rather than pretending the opportunity is unbounded.

> **Probe — "why is your default sort not margin?"** → "Because margin isn't what you make. A 2k margin on an item limited to 8 per four hours is 16k; a 57gp margin on one limited to 13,000 is 741k. The limit is what caps the real prize."

## 9. `decant/DecantService.java` — why doses

Potion forms are different quantities of the same drink, so comparing their prices is invalid. Everything reduces to **price per dose** first:

```
cost per dose    = buy price / doses
revenue per dose = (sell price − tax) / doses
```

The best trade in a family is the cheapest form to buy against the most valuable to sell. Buying and selling the same form is excluded — that's an ordinary flip, and the flip finder already covers it. Both legs must be liquid.

> **Probe — "what's the bug you were avoiding?"** → "Comparing potion prices directly. A 4-dose costing more than a 3-dose isn't a mispricing, it's just more potion. Only per-dose comparison is meaningful, and the test suite would catch anyone reverting that."

## 10. `process/` — alchemy and recipes

`ProcessingService.highAlch` — **no GE tax** applies, because alchemy sells to the game rather than to a player. Missing that makes alching look worse than it is. If the nature rune has no price the method returns nothing rather than something misleading.

`RecipeBuilder` derives recipes from **name patterns** (`Grimy X` → `X`; herb + vial → `X potion (unf)`) rather than a hand-written list. Two families, one of them multi-input. New herbs are picked up for free.

`ProcessingService.recipes` skips any recipe whose inputs aren't all currently priced — a missing price is unknown, not free, and treating it as free would put a fake opportunity at the top.

> **Probe — "why not hard-code the recipes?"** → "Thousands of lines that rot the moment an item is added. The families follow naming rules, so I match those instead."

## 11. `web/` — thin controllers

`FlipController`, `CalculatorController`, `ItemController`. They translate query parameters into a query object and return what the service computed; everything worth testing is in the services, which need no HTTP.

`ApiExceptionHandler` maps causes to codes: a bad parameter → **400**, the upstream API failing → **502** (the failure is upstream and retryable, not our bug), anything unexpected → **500** with a generic message, detail in the logs.

`WebConfig` allows CORS only for configured origins — `*` would let any site on the internet read this API through a visitor's browser. The plugin is unaffected: CORS is a browser rule.

## 12. The tests — what's actually verified

**Unit (27):** the tax rule hardest of all, plus flip arithmetic, ranking, every filter, and the decant per-dose maths. Repository mocked, no services needed.

**Integration (8, Testcontainers + real Postgres):** the Flyway migration runs, catalogue refresh updates instead of duplicating, unknown items are skipped rather than breaking the foreign key, **archiving twice writes nothing the second time**, averages really come from stored history, and an empty upstream response doesn't wipe the catalogue.

> **Probe — "why Testcontainers and not H2?"** → "Because what I'm testing is database behaviour: the migration itself, the foreign keys, the unique index that makes the archive idempotent. H2 would pass happily while the real schema was broken."

## 13. `web/` (React) — three states, one table

`DataTable` exists so loading, empty and error are written once instead of four slightly-different times. Skeletons rather than spinners, so the layout doesn't jump.

`useApi` has two details worth pointing at:

- **Stale responses are discarded** by sequence number. Change a filter twice quickly and two requests are in flight; without the guard the slower one can land last and overwrite newer data with older.
- **A refresh keeps the previous rows.** Blanking the table every sixty seconds makes a live dashboard flicker.

`usePolling` stops while the tab is hidden — no point burning battery and upstream quota on data nobody is looking at.

> **Probe — "what's the hardest bug in a live dashboard?"** → "Out-of-order responses. It looks fine in testing and shows stale data under real use. I version each request and ignore anything that isn't the newest."

## 14. `plugin/` — the RuneLite rules

Two things RuneLite requires, both worth understanding rather than copying:

- **Network access is opt-in with a warning.** `fetchPrices()` defaults to false and carries the mandated IP-address warning. Using the backend is a **separate** opt-in with its own warning, because it sends the player's IP to a server *we* run — a bigger ask than contacting the Wiki.
- **Never block the client thread.** Requests go out via OkHttp's `enqueue()`, and every panel update is pushed onto the Swing thread with `invokeLater`. Either one the other way round freezes the game.

The config group is `"geprofit"` — specific, so it can't collide with another plugin's saved settings.

> **Probe — "what's different about writing a game plugin?"** → "You're a guest in someone else's process. Blocking their thread freezes their game, and their rules say any third-party network call is opt-in with a warning. So all I/O is async and nothing touches the network until the player says yes."

---

## The 60-second walkthrough

> "GE Profit is Grand Exchange analytics with one Spring Boot backend serving two clients — a React dashboard and a RuneLite plugin. The backend polls the OSRS Wiki prices API on three schedules, stores current prices and its own five-minute history in Postgres with Flyway migrations, and exposes three calculators over REST. The interesting part isn't the arithmetic, it's making the numbers honest: ranking by margin times buy limit rather than raw margin, because the limit is what caps what you can actually make; filtering by volume, because a margin nobody trades isn't an opportunity; and working in doses for potions, because the dose forms are different quantities of the same thing. Ingestion is idempotent through a unique index, so a retried poll can't double-count into the averages — and that's tested against real Postgres with Testcontainers, because it's a database guarantee. The plugin follows RuneLite's rules: opt-in network access with the IP warning, and never blocking the client thread."

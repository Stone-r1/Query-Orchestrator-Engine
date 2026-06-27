# Query Orchestrator Engine

A tiny, Spring-free DSL for composing database work as **independent, reusable steps**
that a **scenario assembles into a single session and a single transaction**.

The engine's value is **primarily structural**: it decouples *what an activity does*
from *the resource it runs on*, so activities stay separated and reusable while the scenario
decides which ones run, in what order, and under one shared transaction. That same decoupling
has a measurable performance payoff, too - because a step never owns its session, the runner is
free to fan independent validations out across threads, so the [benchmarks](#benchmarks) below
fall out *for free* from the structure rather than from any special tuning.

---

## The idea

Classic Page-Object / per-activity DAO code welds the **resource lifecycle to the activity**:
every method opens its own connection, runs, commits, and closes. That makes each activity
atomic and self-contained — but it also means you *structurally cannot* assemble several
activities into one transaction. The boundary lives inside the activity, so the caller can
never group them.

This engine inverts that. The unit of work is a **step that receives an ambient session**:

```java
@FunctionalInterface
public interface QuerySteps {
    void execute(Session session); // the step USES a session; it never opens or closes one
}
```

Because a step never owns the session, you get **both** properties at once:

- **Separation** — each step is an independent, reusable activity (what POM gives you).
- **Assembly** — the scenario composes steps over one session + one transaction (what POM can't).

> **The one hard rule:** a step may **use** the session, never **manage** it.
> Lifecycle and the transaction boundary belong to `QueryRunner` alone. The moment a step
> opens, commits, or closes a session, the whole model collapses.

In pattern terms this is the **Unit of Work** with an **injected ambient environment** —
functionally a *Reader over `Session`*: each step is `Session -> effect`, and the scenario
supplies the environment. It's the same slice of value Spring gives you through
`TransactionTemplate` + an injected `EntityManager` + `@Transactional` at the boundary —
rebuilt by hand for codebases where Spring isn't an option.

---

## Why it exists

This was built under a real constraint: **Spring was not allowed** on the job. In that
setting, the test-automation framework separated every activity, which made it impossible to
run several activities inside one transaction. The orchestrator keeps that separation, while
**assembling the activities in the scenario itself** — restoring single-session, single-transaction
composition without pulling in a framework.

---

## Architecture

Three authoring layers over a small runtime:

```
scenarios/   assembly        — picks steps, order, and the transaction boundary
   │
steps/       activities      — named, reusable units of work (compose queries + share state)
   │
queries/     primitives      — session-injected HQL / native SQL, projected to records
   │
util/        runtime         — QueryRunner, QuerySteps, ResultQuery, Ref, ConnectionResolver
```

### Runtime pieces

| Type | Role |
|------|------|
| `QueryRunner` | Owns the lifecycle: resolves a session, opens **one** transaction, runs the steps, commits — or rolls back on failure. |
| `QuerySteps` | The step contract `void execute(Session)`, plus combinators: `selectMode` (run a query and capture its result) and `generalMode` (group steps). |
| `ResultQuery<T>` | A session-injected read that returns a value: `T execute(Session)`. |
| `Ref<T>` | A typed, mutable holder that hands a value from one step to the next **without coupling the steps** — the cross-activity state channel POM lacks. Throws if read before it's set. |
| `ConnectionResolver` / `HibernateSessionResolver` | Resolve a `DbSource` to a Hibernate `Session`; cache the thread-safe `SessionFactory`; auto-discover `@Entity` classes via ClassGraph. |
| `DbSource` | Enum identifying which database a scenario targets. |

---

### Tradeoff to keep in mind

One shared session means steps see each other's **uncommitted** writes (read-your-writes) and
share a single persistence context — which is exactly what `Ref` handoff and scenario assembly
want. The model is therefore **atomic, not isolated-between-steps**: a validation step after an
insert step asserts against *uncommitted* state on the *same* connection. To verify that data is
actually committed and visible to *another* connection, use a separate read session.

---

## Project structure

```
src/main/java/org/example/
  models/
    entities/   Auction, Bid                 — JPA @Entity types
    dto/        AuctionStats, BidRanks, TopBidder — record projections
  queries/      AuctionQueries, BidQueries   — session-injected query primitives
  steps/        (reusable activities)         — compose queries + Ref
  util/         QueryRunner, QuerySteps, ResultQuery, Ref,
                ConnectionResolver, HibernateSessionResolver, DbSource, HelperFunctions
  data/         PathConstants
src/main/resources/
  hibernate.properties   — base Hibernate config
  db.properties          — per-database connection settings
src/test/java/org/example/
  scenarios/    (assembled scenarios)
```

---

## Benchmark workload

The [benchmarks](#benchmarks) below were measured against a seeded database, so the analytical
queries do real work instead of scanning an almost-empty table. Run this script **once before
measuring** - it populates **~200 000 bids** across **500 auctions** and **2000 users**:

```postgresql
-- =============================================================
-- Seed script: ~500 auctions, 2000 users, ~200 000 bids
-- =============================================================

BEGIN;

INSERT INTO auction (
    item_name,
    item_description,
    starting_price,
    start_date,
    end_date,
    max_bid,
    seller_id,
    winner_id
)
SELECT
    'Item #' || s AS item_name,
    'Auto-generated auction item number ' || s AS item_description,
    round((100 + (s % 100) * 99)::numeric, 2) AS starting_price,
    now() - ((90 - (s % 90)) || ' days')::interval AS start_date,
    now() + ((1 + (s % 30)) || ' days')::interval AS end_date,
    round((100 + (s % 100) * 99) * (1.5 + (s % 6) * 0.5)::numeric, 2) AS max_bid,
    (s % 100) + 1 AS seller_id,
    NULL AS winner_id
FROM generate_series(1, 500) AS s;

-- ----------------------------------------------------------------
-- Bids: 400 bids per auction * 500 auctions = 200 000 rows
-- ----------------------------------------------------------------
INSERT INTO bid (
    user_id,
    auction_id,
    amount,
    placed_at
)
SELECT
    ((row_number() OVER () - 1) % 2000) + 1 AS user_id,
    a.auction_id,
    round((
        a.starting_price
        + (((row_number() OVER () - 1) % 2000) * 0.5)
        + (random() * a.starting_price * 0.2)
    )::numeric, 2) AS amount,
    a.start_date + (random() * (a.end_date - a.start_date)) AS placed_at
FROM auction a
CROSS JOIN generate_series(1, 400) AS u(n);

COMMIT;
```

## Benchmarks

Both suites run the **same six scenarios** - identical SQL, identical assertions, against the
**~200 000-bid** workload seeded above. The only thing that changes is the runner:

- **Classic JDBC** - every validation opens its own connection and the steps run **sequentially**,
  the way most test-automation DAOs are written.
- **QueryRunner** - the independent validations inside a scenario are dispatched **in parallel**
  through `validateInParallel`, each on its own read-only session.

Average per-step wall-clock, **5 runs each**:

| Step | What it validates                                 |  Classic JDBC |  QueryRunner | Speedup |
|-----:|---------------------------------------------------|--------------:|-------------:|--------:|
| 1 | Bid totals - *sequential `await` in both*         |        136 ms |       133 ms | ~1.0× |
| 2 | Leaderboards - 6 trivial reads                    |        147 ms |       224 ms | 0.7× |
| 3 | Bid analysis - 6 reads incl. heavy outbid scan ×2 |     14 900 ms |     7 465 ms | ~2.0× |
| 4 | User profiles - 6 reads                           |        182 ms |        54 ms | ~3.4× |
| 5 | Price metrics - 6 reads                           |        143 ms |        31 ms | ~4.6× |
| 6 | Intensity & heatmap - 6 reads                     |        146 ms |        29 ms | ~5.0× |
| | **Total**                                         | **15 654 ms** | **7 936 ms** | **~2.0×** |

### Reading the numbers

- **Step 3 is the headline.** It carries the two heaviest queries in the suite (a global outbid
  aggregation run once per auction). Overlapping the six validations roughly **halves** the
  wall-clock - the slow query stops blocking everything queued behind it.
- **Steps 4–6 show the same effect** at 3–5×: several independent reads run at once instead of
  one after another.
- **Step 1 is the control.** It uses sequential `await` in *both* suites, and the times come out
  even (136 vs 133 ms). That does double duty — it isolates parallelism as the only variable, and
  it shows session/connection setup isn't skewing the comparison. The later gaps are real work
  overlapping, not plumbing.
- **Step 2 is the honest caveat.** When every query returns a handful of rows, the cost of spinning
  up threads and extra sessions **outweighs** the saving, so parallel comes out slightly slower.
  Parallel validation earns its keep on non-trivial or plentiful reads — it isn't a blanket win.

> **What this shows:** the speedup comes from **how the scenario assembles independent
> validations**, not from Hibernate being faster than JDBC - the SQL is byte-for-byte identical on
> both sides. Any JDBC suite could match it by hand-rolling a thread pool and a connection per task;
> the orchestrator just makes that the **default, declarative path** instead of per-test boilerplate.

## Tech stack

- **Java 21**
- **Hibernate ORM 7** — session, transactions, HQL + native queries
- **ClassGraph** — automatic `@Entity` discovery (no manual class registration)
- **Lombok** — entity boilerplate

---

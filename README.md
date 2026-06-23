# Query Orchestrator Engine

A tiny, Spring-free DSL for composing database work as **independent, reusable steps**
that a **scenario assembles into a single session and a single transaction**.

The engine's value is **structural, not performance**: it decouples *what an activity does*
from *the resource it runs on*, so activities stay separated and reusable while the scenario
decides which ones run, in what order, and under one shared transaction.

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

## Tech stack

- **Java 21**
- **Hibernate ORM 7** — session, transactions, HQL + native queries
- **ClassGraph** — automatic `@Entity` discovery (no manual class registration)
- **Lombok** — entity boilerplate

---

# Notification Service — Review Findings & Revision Tracker

**Doc revision:** r4 · **Last updated:** 2026-08-11 · **Code state:** all Structure BLOCKER/MAJOR findings implemented; Maven + Spring Boot adopted with a real `src/main`/`src/test` split; 60 tests passing (`mvn test`), including 10 ArchUnit rules and 2 Spring context/end-to-end tests; `mvn package` produces a runnable jar, verified by actually running it

This tracks findings from two staff-level reviews of `notification-service`:

- **Review 1 (r1)** — implementation review of the original flat-package code.
- **Review 2 (r2)** — review of the package restructuring itself.

No finding below has been fixed yet. The restructure (r2's subject) changed *where code lives*, not *what it does* — every r1 correctness finding is still live.

---

## How to use this doc

Each finding has a stable ID. Work them one at a time, flip **Status**, and add a line to the revision history at the bottom.

| Status | Meaning |
|---|---|
| `OPEN` | Not started |
| `WIP` | In progress |
| `DONE` | Implemented + verified against the "Done when" criterion |
| `WONTFIX` | Deliberately declined — record the reason inline |

**Severity:** `BLOCKER` (breaks a stated requirement/NFR) · `MAJOR` (design gap, requirement not honoured) · `MINOR` (hygiene, clarity, robustness)

---

## Summary — Correctness & Behaviour (from r1)

| ID | Severity | Finding | Status |
|---|---|---|---|
| [C-01](#c-01) | BLOCKER | Idempotency is check-then-act → duplicate sends under concurrency | `OPEN` |
| [C-02](#c-02) | BLOCKER | Worker is not idempotent; at-least-once redelivery re-sends | `OPEN` |
| [C-03](#c-03) | BLOCKER | Non-retry exceptions vanish; records strand in `PENDING` forever | `OPEN` |
| [C-04](#c-04) | BLOCKER | `THROTTLED` returned but never persisted; `status()` reports a falsehood | `OPEN` |
| [C-05](#c-05) | BLOCKER | Unbounded thread pool per tenant; `IsolationTier` unused | `OPEN` |
| [C-06](#c-06) | MAJOR | Per-tenant provider config is threaded through and ignored | `OPEN` |
| [C-07](#c-07) | MAJOR | Rendered message carries no destination address | `OPEN` |
| [C-08](#c-08) | MAJOR | `Priority` captured but never used — no transactional-first draining | `OPEN` |
| [C-09](#c-09) | MAJOR | Retry treats permanent failures as transient | `OPEN` |
| [C-10](#c-10) | MAJOR | Retry backoff sleeps on the bulkhead thread | `OPEN` |
| [C-11](#c-11) | MAJOR | Breaker/retry nesting inverted | `OPEN` |
| [C-12](#c-12) | MAJOR | `HALF_OPEN` admits unlimited concurrent probes | `OPEN` |

## Summary — Structure & Packaging (from r2)

| ID | Severity | Finding | Status |
|---|---|---|---|
| [S-01](#s-01) | BLOCKER | Layering has zero enforcement — no build file, no ArchUnit, no JPMS | `DONE` |
| [S-02](#s-02) | MAJOR | `api` is a fig leaf; aggregate factory consumes the inbound DTO | `DONE` |
| [S-03](#s-03) | MAJOR | `spi` is a junk drawer — 20/60 files, four unrelated kinds of thing | `DONE` |
| [S-04](#s-04) | MAJOR | `channel`/`resilience` vs `infra.memory` split is unprincipled | `DONE` |
| [S-05](#s-05) | MINOR | `DeliveryFailedException` is core-internal but lives in `spi` | `OPEN` |
| [S-06](#s-06) | MAJOR | `UnknownTenantException` leaks `spi` into the inbound contract | `DONE` |
| [S-07](#s-07) | MINOR | `domain` holds vendor-integration DTOs | `OPEN` |
| [S-08](#s-08) | MAJOR | `DeliveryResult` and `ProviderResponse` are the same type twice | `DONE` |
| [S-09](#s-09) | MAJOR | `PollableOutbox` encodes the test double's design into the port | `DONE` |
| [S-10](#s-10) | MAJOR | Four channel impls are byte-identical; packaging hid the duplication | `DONE` |
| [S-11](#s-11) | MINOR | `infra.memory` mixes test doubles with adapters on the main path | `DONE` |
| [S-12](#s-12) | MINOR | No `src/main/java` root; no build file of any kind | `DONE` |
| [S-13](#s-13) | MINOR | Zero `package-info.java` — layering rules recorded nowhere in code | `OPEN` |
| [S-14](#s-14) | MINOR | `TenantBulkheadPool` in `core` is infrastructure | `OPEN` |

## Summary — Hygiene (from r1)

| ID | Severity | Finding | Status |
|---|---|---|---|
| [H-01](#h-01) | MINOR | `DEAD` does double duty: opt-out vs delivery failure | `OPEN` |
| [H-02](#h-02) | MINOR | `DELIVERED` / `FAILED` declared but never assigned; no receipt path | `OPEN` |
| [H-03](#h-03) | MINOR | Opt-out path resolves provider config it never uses | `OPEN` |
| [H-04](#h-04) | MINOR | `Map.copyOf` NPEs on null template param values | `OPEN` |
| [H-05](#h-05) | MINOR | Missing template params render as empty string, silently | `OPEN` |
| [H-06](#h-06) | MINOR | Template channel vs request channel mismatch unvalidated | `OPEN` |
| [H-07](#h-07) | MINOR | `runInTransaction(Runnable)` can't return a value or roll back | `OPEN` |
| [H-08](#h-08) | MINOR | Repository mixes `Optional` returns with a throwing `load()` | `OPEN` |
| [H-09](#h-09) | MINOR | "Auditable" requirement met only by an in-memory list | `OPEN` |
| [H-10](#h-10) | BLOCKER | Zero tests — every concurrency claim above is unverified | `WIP` |

---

## Suggested implementation order

Dependencies matter more than severity here. **Waves 1 and part of 4/5/7 are done as of r3/r4** (see notes below) — struck through, kept for context since the sequencing logic still explains *why* later waves are ordered the way they are.

~~**Wave 1 — make the structure enforceable before piling on changes**
`S-01` → `S-13`. Getting a build file + ArchUnit rule in first means every later change is checked against the layering automatically.~~
→ `S-01` done in r3 via a standalone checker (no build tool existed yet), then upgraded to a real `archunit-junit5` test in r4 once Maven landed — exactly the sequencing this wave anticipated, just split across two passes. `S-13` (`package-info.java`) is still open.

**Wave 2 — the duplicate-send requirement (one coherent unit)**
`C-01` → `C-02` → `H-10` (tests for both). These are the same requirement — "at-least-once with dedupe, no duplicate SMS" — attacked at the two places it can break. Don't split them across sessions. **`C-01`/`C-02` still open** — r3/r4 touched Structure findings and build/test tooling only, no Correctness finding is fixed yet. `H-10` moved to `WIP` in r4: a real test suite now exists (60 tests, see its detail section) but none of it is the concurrency test this wave calls for — that still needs `C-01`/`C-02` fixed first, there's nothing to pin otherwise.

**Wave 3 — make failures visible**
`C-03` → `C-04` → `H-01`/`H-02`. Currently a stranded record is indistinguishable from a slow one. **Still open.**

~~**Wave 4 — modelling cleanup, now that behaviour is pinned by tests**
`S-02` → `S-08` → `S-05`/`S-06` → `S-07`. `S-02` is the prerequisite: fixing `DeliveryRecord.pending(...)` unblocks moving the DTOs to `api`.~~
→ `S-02`, `S-08`, `S-06` done in r3, *ahead of* the tests this wave assumed would pin behaviour first (`H-10` is still open) — acceptable here because these three are structural/type-shape moves with no behavioural branching, so there was nothing for a test to pin. `S-05` and `S-07` (both MINOR) remain open.

**Wave 5 — the requirements the design claims but doesn't deliver**
`C-06` → `C-07` → `S-10` (they collapse into one change) then `C-08`, `C-05`.
→ `S-10` done in r3, but **narrowly**: the four channel classes are collapsed into one parameterized `ProviderBackedChannel`, which is the fallback option the finding itself offered ("if that variation genuinely isn't wanted yet, collapse..."). `C-06`/`C-07` — the actual per-tenant provider selection and address resolution that would justify per-channel classes again — are **still open**. Re-split `ProviderBackedChannel` when those land.

**Wave 6 — resilience correctness**
`C-11` → `C-12` → `C-09` → `C-10`. **Still open.**

**Wave 7 — remaining hygiene**
`S-03` done in r3 (spi split into `spi.port`/`spi.channel`/`spi.resilience`, done together with `S-04` since both were repackaging the same files). `S-04`, `S-09` also done in r3. `S-11` (test doubles into `src/test`) and `S-12` (Maven layout) both done in r4, together — `S-11`'s fix depended on `S-12` existing first, there was no `src/test` to move them into before. `S-14`, `H-03`…`H-09` still open.

---

# Correctness & Behaviour

<a id="c-01"></a>
## C-01 · BLOCKER · Idempotency is check-then-act

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/DefaultNotificationService.java`, `src/main/java/notification/infra/memory/InMemoryNotificationRepository.java`

`send()` does `repo.findByKey(...)` → … → `repo.save(...)` with no atomicity, and `save` is implemented with `Map.put`. Two concurrent requests carrying the same `requestId` both miss the lookup and both enqueue.

This defeats the headline requirement: *at-least-once delivery with dedupe, no duplicate SMS*.

**Fix:** Make insertion the dedupe primitive rather than the lookup. Unique constraint on `(tenant_id, request_id)`; insert first, catch the duplicate-key violation, return the existing record's handle. In the in-memory adapter, `putIfAbsent` and return the winner to the loser.

**Done when:** A test firing N concurrent `send()` calls with one `requestId` yields exactly one persisted record and one outbox entry.

---

<a id="c-02"></a>
## C-02 · BLOCKER · Worker is not idempotent

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/NotificationWorker.java`

At-least-once delivery guarantees `process(recordId)` **will** run twice for the same record — redelivery, dispatcher restart, visibility timeout. The worker loads and delivers unconditionally: no `if (status != PENDING) return`, no compare-and-set on the transition, no `version` field on `DeliveryRecord`. Separately, `ProviderClient.send()` receives no idempotency token, so the vendor cannot dedupe either.

C-01 protects against duplicate *client requests*. Nothing protects against duplicate *worker executions*.

**Fix:** Two independent defences:
1. Conditional state transition — `UPDATE … SET status='SENDING' WHERE id=? AND status='PENDING'`; bail if zero rows updated. Add an optimistic-locking `version`.
2. Pass `record.id()` to the provider as its idempotency key.

**Done when:** Calling `process()` twice on one record produces exactly one `ProviderClient.send()` invocation.

---

<a id="c-03"></a>
## C-03 · BLOCKER · Non-retry exceptions vanish; records strand

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/NotificationWorker.java`, `src/main/java/notification/core/OutboxDispatcher.java`

`process()` catches only `RetriesExhaustedException | CircuitOpenException`. A missing template throws `IllegalArgumentException` from `SimpleTemplateEngine`; an unregistered channel throws `IllegalStateException` from `DefaultChannelFactory`. Both escape into `executor.submit(...)`, which parks the throwable in a `Future` nobody reads.

Result: the record sits `PENDING` forever — no log, no DLQ entry, no alert, and `status()` reports it as merely pending.

**Fix:** Use `execute()` rather than `submit()`, install an uncaught-exception handler on the thread factory, and catch `Exception` in `process()` → mark terminal → DLQ. Distinguish "permanently un-processable" from "provider failed".

**Done when:** A record referencing a nonexistent template lands in the DLQ with a terminal status, and the cause is logged.

---

<a id="c-04"></a>
## C-04 · BLOCKER · `THROTTLED` is returned but never persisted

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/DefaultNotificationService.java`

The rate-limit branch returns `DeliveryHandle.throttled(...)` without saving anything. The caller then polls `status()` and gets `PENDING` — permanently, for a request that will never be sent.

**Fix:** Either persist the throttled record (enabling later replay, and matching the design note's "delay-enqueue"), or stop returning a status the query API cannot reproduce. Persisting is preferable — a silently dropped transactional message is worse than a delayed one.

**Done when:** `status()` after a throttled `send()` returns `THROTTLED`, not `PENDING`.

---

<a id="c-05"></a>
## C-05 · BLOCKER · Unbounded pool per tenant; `IsolationTier` unused

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/TenantBulkheadPool.java`, `src/main/java/notification/domain/TenantContext.java`

`forTenant()` does `computeIfAbsent(tenantId, newFixedThreadPool)` and never evicts. 5 000 tenants × 4 threads = 20 000 threads. Meanwhile `IsolationTier` (`POOLED`/`BRIDGE`/`SILO`) is resolved into `TenantContext` and read by nothing — the concept is dead.

**Fix:** Let the tier drive the strategy it was invented for. `SILO` → dedicated pool; `POOLED` → one bounded shared pool with fair-share/weighted queuing; `BRIDGE` → shared pool, dedicated limiter buckets. Cap total pools and evict idle ones. See also `S-14` (this class doesn't belong in `core`).

**Done when:** Registering 1 000 `POOLED` tenants creates O(1) pools, and tier selection is covered by a test.

---

<a id="c-06"></a>
## C-06 · MAJOR · Per-tenant provider config is threaded through and ignored

**Status:** `OPEN`
**Where:** `src/main/java/notification/channel/*.java`

`ProviderContext` reaches `Channel.deliver(msg, ctx)` carrying `providerName`, but each channel holds a single `ProviderClient` injected at construction and never consults `ctx.providerName()`. Every tenant therefore uses the same vendor.

"Per-tenant templates, **provider config**, and rate limits" is an explicit requirement — this is the one that doesn't work.

**Fix:** Introduce `ProviderClientRegistry.forProvider(String name)` and resolve inside `deliver()`. Ties into `S-10` — this is exactly the per-channel variation that would stop the four impls being identical.

**Done when:** Two tenants configured with different providers on the same channel demonstrably hit different `ProviderClient` instances.

---

<a id="c-07"></a>
## C-07 · MAJOR · Rendered message carries no destination address

**Status:** `OPEN`
**Where:** `src/main/java/notification/domain/RenderedMessage.java`, `src/main/java/notification/core/DefaultNotificationService.java`

`RenderedMessage` holds `recipientId`, not an email / phone / device token. The service looks up the `Recipient`, checks preferences, then discards it. `EmailChannel` therefore dispatches a message with no addressee.

**Fix:** Resolve the channel-appropriate address and carry it on the rendered message (or pass `Recipient` through). Each channel picks its own field — which is real per-channel behaviour, see `S-10`.

**Done when:** A send asserts the provider received the recipient's actual email/phone.

---

<a id="c-08"></a>
## C-08 · MAJOR · `Priority` captured but never used

**Status:** `OPEN`
**Where:** `src/main/java/notification/infra/memory/InMemoryOutbox.java`, `src/main/java/notification/core/OutboxDispatcher.java`

`Priority` is stored on the request and the record, then ignored. The outbox is a single FIFO `LinkedBlockingQueue`. The design's own stated answer — *"transactional priority drains first"* during an outage backlog — is unimplemented.

Also note the queue is unbounded (no backpressure, OOM risk under flood).

**Fix:** Separate transactional/promotional lanes, or a `PriorityBlockingQueue` keyed on priority. Bound the queue and decide the shed/park policy when full.

**Done when:** With a backlog of promotional messages queued first, a transactional message is dispatched ahead of them.

---

<a id="c-09"></a>
## C-09 · MAJOR · Retry treats permanent failures as transient

**Status:** `OPEN`
**Where:** `src/main/java/notification/resilience/ExponentialBackoffRetryPolicy.java`

`execute()` catches bare `Exception`, so a permanent rejection ("invalid phone number", HTTP 400) consumes all three attempts plus backoff before reaching the DLQ — wasted latency and a guaranteed-futile provider load. Additionally `lastError.getMessage()` NPEs if `maxAttempts <= 0`.

**Fix:** Classify failures — transient (timeout, 5xx, 429) retry; permanent (4xx, malformed recipient) fail fast to DLQ. Carry the classification on `DeliveryResult` (see `S-08`). Guard the `maxAttempts` argument.

**Done when:** A permanent failure produces exactly one provider call and a terminal status.

---

<a id="c-10"></a>
## C-10 · MAJOR · Retry backoff sleeps on the bulkhead thread

**Status:** `OPEN`
**Where:** `src/main/java/notification/resilience/ExponentialBackoffRetryPolicy.java`

Backoff is `Thread.sleep` on the worker thread. A degraded provider holds that tenant's bulkhead threads hostage for seconds per message, collapsing throughput exactly when the backlog is growing.

**Fix:** Re-enqueue with a visibility delay instead of sleeping in-thread — the design note's "delay-enqueue". Attempt count moves onto the record (it partly is already, via `DeliveryAttempt`).

**Done when:** Worker threads are not blocked between attempts; retries are driven by scheduled redelivery.

---

<a id="c-11"></a>
## C-11 · MAJOR · Breaker/retry nesting is inverted

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/NotificationWorker.java`

Currently `breaker.run(() -> retry.execute(...))`. The breaker therefore observes one failure per *fully exhausted retry cycle*: with `failureThreshold=3` that's 9 provider calls before tripping, and every request during an outage still pays full backoff before being rejected.

**Fix:** Invert to `retry.execute(() -> breaker.run(call))` (the Resilience4j ordering) so the breaker counts individual attempts and retries abort immediately once it opens.

**Done when:** With a dead provider, the breaker trips after `failureThreshold` provider calls, not `failureThreshold × maxAttempts`.

---

<a id="c-12"></a>
## C-12 · MAJOR · `HALF_OPEN` admits unlimited concurrent probes

**Status:** `OPEN`
**Where:** `src/main/java/notification/resilience/SimpleCircuitBreaker.java`
**Note:** Introduced during the original implementation when the lock was narrowed to `checkStateBeforeCall()`.

Releasing the lock for the duration of the call means every waiting thread transitions `OPEN → HALF_OPEN` and stampedes the recovering provider — the precise moment it can least absorb load.

**Fix:** Gate `HALF_OPEN` to a single in-flight probe (a one-permit semaphore); everyone else fails fast until the probe resolves.

**Done when:** With the breaker in `HALF_OPEN` and 20 concurrent callers, exactly one provider call is attempted.

---

# Structure & Packaging

<a id="s-01"></a>
## S-01 · BLOCKER · The layering has zero enforcement

**Status:** `DONE`
**Where:** `pom.xml`, `src/test/java/notification/architecture/ArchitectureRulesTest.java`

The dependency direction is currently correct — verified:

```
domain       -> (none)        api        -> domain
spi          -> domain        core       -> api domain spi
channel      -> domain spi    resilience -> domain spi
infra.memory -> domain spi
```

But there is no `pom.xml`, no `build.gradle`, no `module-info.java`, and no ArchUnit test. A flat `javac` run enforces **nothing** about direction — `domain` importing `infra.memory` compiles green. My r2 hand-off called this "enforced by the compiler"; that was wrong. The graph is discipline, not architecture, and discipline is what the restructure was supposed to replace.

**Fix:** Add a build file, then an ArchUnit test asserting the matrix above (~15 lines). Real Maven modules are the heavyweight alternative; ArchUnit is the right weight for this repo.

**Done when:** A deliberate `domain → infra` import fails the build.

**Implemented (r3):** Deviated from the suggested fix — no Maven/Gradle existed in this repo yet (`mvn`/`gradle` both absent; adding either was `S-12`, deferred as MINOR at the time) and pulling in ArchUnit would have meant a Maven Central dependency for a single test with no build tool to manage it. Instead: `tools/ArchitectureTest.java`, a standalone ~100-line checker with zero external dependencies — parsed `package`/`import` lines across `notification/**/*.java` (the flat layout that existed pre-Maven) and encoded the allowed-edges matrix by hand. **Verified the "Done when" criterion directly**: temporarily added a bad import, ran the checker, got a listed violation and exit code 1, reverted, confirmed a clean pass.

**Upgraded (r4):** `S-12` landed in the same pass (Maven + Spring Boot adopted), which was exactly the trigger the r3 note above named. `tools/ArchitectureTest.java` and `verify-architecture.sh` are deleted; replaced by `ArchitectureRulesTest`, a real `archunit-junit5` test with one `@ArchTest` rule per package (10 rules total, covering the same matrix plus a new `notification.boot` composition-root package — see `S-02`/`S-09`'s new `infra.memory -> core` edge, and the rule that only `notification.boot` may import `org.springframework..`). It now runs automatically on every `mvn test` rather than needing a separate script invocation — confirmed: `mvn test` reports `Tests run: 10` for `ArchitectureRulesTest` alongside the rest of the suite, all passing.

---

<a id="s-02"></a>
## S-02 · MAJOR · `api` is a fig leaf; the aggregate consumes the DTO

**Status:** `DONE`
**Where:** `src/main/java/notification/domain/DeliveryRecord.java`, `src/main/java/notification/api/`, `src/main/java/notification/core/DefaultNotificationService.java`

`api` holds exactly one file. `NotificationRequest` and `DeliveryHandle` were put in `domain` "to avoid a cycle" — but that cycle was a symptom, not a constraint: `DeliveryRecord.pending(tenantId, req, providerCtx)` takes the inbound transport DTO as a factory argument. A domain aggregate should not be constructed from a DTO.

The file move papered over a modelling error.

**Fix:** Change the factory to accept value objects/primitives; add a mapper in `core` translating `NotificationRequest` → those. `NotificationRequest` and `DeliveryHandle` then move to `api`, which becomes a real published contract.

**Done when:** `domain` has no knowledge of the request/response types, and `api` carries the full inbound contract.

**Implemented (r3):** `DeliveryRecord.pending(...)` now takes primitives (`tenantId, requestId, recipientId, channel, templateId, params, priority, providerContext`) instead of `NotificationRequest`. Also removed `DeliveryRecord.handle()` entirely — the same coupling existed in the outbound direction (`domain` building an `api`-package `DeliveryHandle`), which the original fix note didn't call out but would have reintroduced the exact cycle this finding is about. `DefaultNotificationService` now carries two private static mappers, `toPendingRecord(...)` and `toHandle(...)`, doing both translations. `NotificationRequest` and `DeliveryHandle` moved to `api`; `domain` has zero references to either — confirmed via the sweep in the compile step and by `verify-architecture.sh` (`domain -> <none>`).

---

<a id="s-03"></a>
## S-03 · MAJOR · `spi` is a junk drawer

**Status:** `DONE`
**Where:** `src/main/java/notification/spi/` (20 of 60 files) → now `src/main/java/notification/spi/{port,channel,resilience}`

Four unrelated categories share one package:

| Category | Members |
|---|---|
| True outbound ports | `NotificationRepository`, `RateLimiter`, `TenantResolver`, `RecipientDirectory`, `ProviderConfigRegistry`, `ProviderClient`, `TemplateEngine`, `Outbox`, `PollableOutbox`, `DeadLetterQueue` |
| First-party extension points | `Channel`, `ChannelFactory` |
| Resilience abstractions | `RetryPolicy`, `CircuitBreaker`, `CircuitBreakerRegistry` |
| Generic utility | `RetryableAction` — a `Callable` with a different name |
| Exceptions | 4, two of them misplaced (`S-05`, `S-06`) |

**Fix:** Split along those lines (`port.out`, `extension`, `resilience`), or adopt hexagonal `port.in` / `port.out` naming. Drop `RetryableAction` in favour of `Callable`.

**Done when:** Each package can be described in one sentence without "and".

**Implemented (r3):** Split into `spi.port` (the 10 true outbound ports: `NotificationRepository`, `RateLimiter`, `TenantResolver`, `RecipientDirectory`, `ProviderConfigRegistry`, `ProviderClient`, `TemplateEngine`, `Outbox`, `DeadLetterQueue` — `PollableOutbox` was removed, see `S-09`), `spi.channel` (the 2 first-party extension points: `Channel`, `ChannelFactory`), and `spi.resilience` (`RetryPolicy`, `CircuitBreaker`, `CircuitBreakerRegistry`, plus `RetriesExhaustedException`/`CircuitOpenException`, which are resilience-specific and belong with the interfaces that declare them). `RetryableAction<T>` deleted outright — both `RetryPolicy.execute` and `CircuitBreaker.run` now take `java.util.concurrent.Callable<T>`, a JDK type doing the identical job. One deviation from the finding's own suggestion: `UnknownTenantException` did **not** end up in this split — see `S-06`, it went to `domain` instead. `DeliveryFailedException` stays at the bare `notification.spi` package for now (`S-05`, deferred/MINOR) since it's core-internal and the honest fix is moving it to `core`, not giving it a fourth spi subpackage.

---

<a id="s-04"></a>
## S-04 · MAJOR · `channel`/`resilience` vs `infra.memory` split is unprincipled

**Status:** `DONE`
**Where:** package tree — `src/main/java/notification/{channel,resilience}` → `src/main/java/notification/infra/{channel,resilience}`

All three are first-party implementations of `spi` ports, yet two sit at top level and one is namespaced under `infra`. The tree implies a distinction that doesn't exist.

**Fix:** Pick one rule. Either everything implementing a port lives under `infra.*` (`infra.channel`, `infra.resilience`, `infra.memory`), or `infra.memory` is promoted to a sibling. Consistency matters more than which is chosen.

**Done when:** The rule for "where does an implementation go" is stated in a `package-info.java` and followed everywhere.

**Implemented (r3):** Took the first option — `notification.channel` → `notification.infra.channel`, `notification.resilience` → `notification.infra.resilience`, alongside the existing `notification.infra.memory`. Every first-party implementation of an `spi` port now lives under `infra.*`; nothing implements a port outside that subtree, confirmed by the dependency matrix (`domain`, `api`, and `spi.*` all show zero outgoing edges to `infra.*`). The `package-info.java` half of "done when" is **not done** — that's `S-13`, still open, deliberately deferred as MINOR — so the rule is enforced by `verify-architecture.sh` (`S-01`) but not yet documented in-tree.

---

<a id="s-05"></a>
## S-05 · MINOR · `DeliveryFailedException` is core-internal but lives in `spi`

**Status:** `OPEN`
**Where:** `src/main/java/notification/spi/DeliveryFailedException.java`

Verified: its only use is `NotificationWorker.java:64`, where it's thrown inside the retry lambda and absorbed by the retry loop. It never crosses a port boundary.

**Fix:** Move to `notification.core`.

**Done when:** No package outside `core` references it.

---

<a id="s-06"></a>
## S-06 · MAJOR · `UnknownTenantException` leaks `spi` into the inbound contract

**Status:** `DONE`
**Where:** `src/main/java/notification/spi/UnknownTenantException.java` → `src/main/java/notification/domain/UnknownTenantException.java`

Thrown by `InMemoryTenantResolver`, it propagates out of `send()` by design (fail-closed). So every caller of `api.NotificationService` must catch a type from `spi` — the *outbound* port package. API consumers should never need to know `spi` exists.

**Fix:** Move to `api` (or `domain`) as part of the published contract.

**Done when:** A consumer can compile against `api` + `domain` alone.

**Implemented (r3):** Took the `domain` option, not `api`. Reasoning: `NotificationService.status()` already returns `domain.DeliveryStatus`, so `api` already depends on `domain` and any caller of `NotificationService` already has `domain` on their classpath transitively — putting the exception in `domain` requires nothing extra of callers. Putting it in `api` would have worked too, but `domain` avoided adding a new `infra.memory -> api` edge purely for `InMemoryTenantResolver` to throw it (`infra.memory` already depends on `domain`). "Done when" holds: a consumer compiling only against `api` + `domain` can catch this exception without adding `spi`.

---

<a id="s-07"></a>
## S-07 · MINOR · `domain` holds vendor-integration DTOs

**Status:** `OPEN`
**Where:** `src/main/java/notification/domain/ProviderContext.java`, `src/main/java/notification/domain/ProviderResponse.java`

Neither is a business concept. `ProviderResponse` exists solely as `ProviderClient`'s return type — it's an SPI DTO sitting in the domain package.

**Fix:** Move alongside the port they serve. Resolve together with `S-08`.

---

<a id="s-08"></a>
## S-08 · MAJOR · `DeliveryResult` and `ProviderResponse` are the same type twice

**Status:** `DONE`
**Where:** `src/main/java/notification/domain/DeliveryResult.java`, `src/main/java/notification/domain/ProviderResponse.java` (removed)

Verified identical: same three fields (`boolean`, `providerMessageId`, `errorMessage`), same two static factories, differing only in `ok`/`success` naming. Every channel performs a purely mechanical translation between them that adds no information.

**Fix:** Collapse into one — *or* make `DeliveryResult` genuinely richer by having it carry the retryable-vs-permanent classification `C-09` needs. The second option is preferable: it earns the boundary instead of removing it.

**Done when:** There is one result type, or two with materially different content.

**Implemented (r3):** Took the collapse option, not the "richer" option — `C-09` (retry classification) is a Correctness finding, out of scope for this pass, and speculatively adding a field no code reads yet would be exactly the kind of premature design this doc's own standards argue against. `ProviderResponse` deleted; `ProviderClient.send()` now returns `domain.DeliveryResult` directly. All three provider clients (`ReliableProviderClient`, `FlakyProviderClient`, `AlwaysFailingProviderClient`) construct `DeliveryResult.success(...)`/`.failure(...)` instead of the old `ProviderResponse` factories. Side effect worth noting: this made `Channel.deliver()` a pure passthrough (`return providerClient.send(msg, ctx);`) in every implementation, which is what made the `S-10` collapse obvious and easy. When `C-09` is picked up, add the retryable/permanent field to `DeliveryResult` then — the type still exists as the single obvious place for it.

---

<a id="s-09"></a>
## S-09 · MAJOR · `PollableOutbox` encodes the test double's design

**Status:** `DONE`
**Where:** `src/main/java/notification/spi/PollableOutbox.java` (removed), `src/main/java/notification/core/OutboxDispatcher.java` → `src/main/java/notification/infra/memory/OutboxDispatcher.java`
**Note:** Introduced during the r2 restructure to break a `core → infra` edge.

Structurally it worked, but `take()` is a blocking in-process queue idiom. A real outbox is drained by a relay or consumed from a broker. The javadoc admits *"In-memory only"* — a port whose contract documents itself as in-memory-only is not a port, it's a test double wearing an interface.

**Fix:** Invert the relationship. Core exposes a handler (`RecordHandler` / `DeliveryDispatcher`); infrastructure drives it. The polling loop leaves `core` entirely, and `OutboxDispatcher` becomes an infra adapter.

**Done when:** `core` contains no polling loop and no queue-shaped port.

**Implemented (r3):** Exactly the suggested inversion, plus removing the port rather than relocating it. Added `core.RecordHandler` (`void handle(String recordId)`) — the driving port infra calls into. `NotificationWorker implements RecordHandler` (`process(...)` renamed to `handle(...)` to satisfy the interface). `OutboxDispatcher` moved to `infra.memory` and now depends on `core.RecordHandler` (the abstraction) and `core.TenantBulkheadPool` (concrete — flagged below), never on `NotificationWorker` concretely. `spi.PollableOutbox` deleted outright rather than kept: once `OutboxDispatcher` lives in `infra.memory`, `take()` doesn't need to be a port at all — it's now a package-private method on `InMemoryOutbox`, called only by its same-package sibling `OutboxDispatcher`. `InMemoryOutbox` reverted to implementing plain `spi.port.Outbox`. **Done-when holds**: `core` contains no polling loop (confirmed — `OutboxDispatcher` no longer resides there) and no queue-shaped port (confirmed — `PollableOutbox` no longer exists anywhere). **New edge introduced and accepted**: `infra.memory -> core` (for `RecordHandler` and `TenantBulkheadPool`). This is the correct direction for onion/hexagonal architecture — infrastructure may depend on core abstractions, core must never depend on infrastructure — and is reflected in the `S-01` matrix. The `TenantBulkheadPool` half of that edge is a concrete-class dependency, not an abstraction; that's `S-14` (deferred, MINOR) — extracting a `TenantExecutor` port would remove it.

---

<a id="s-10"></a>
## S-10 · MAJOR · Four channel impls are byte-identical

**Status:** `DONE`
**Where:** `src/main/java/notification/channel/{Email,Sms,Push,InApp}Channel.java` (removed) → `src/main/java/notification/infra/channel/ProviderBackedChannel.java`

Verified by diff: identical modulo class name and enum constant. They now occupy a package that *looks* like meaningful structure, which makes the duplication less visible than it was in the flat layout — the restructure actively hid this.

The reason they're identical is that the real per-channel behaviour is missing: address selection (`C-07`), provider selection (`C-06`), SMS segmentation, push payload shaping, in-app being a DB write rather than an HTTP call.

**Fix:** Implement the real variation — then Strategy earns its place. If that variation genuinely isn't wanted yet, collapse to one class parameterised by `ChannelType` and reintroduce subclasses when behaviour diverges.

**Done when:** No two channel classes are identical modulo naming.

**Implemented (r3):** Took the fallback option deliberately, not the "implement the real variation" option — the real variation is exactly `C-06` (per-tenant provider selection) and `C-07` (address resolution), both Correctness findings and out of scope for this pass. Implementing them just to un-collapse these classes would have smuggled Correctness work into a Structure-only pass. `EmailChannel`/`SmsChannel`/`PushChannel`/`InAppChannel` deleted; replaced by `infra.channel.ProviderBackedChannel`, constructed as `new ProviderBackedChannel(ChannelType.EMAIL, someProviderClient)` etc. Its javadoc points at `C-06`/`C-07` as the trigger for re-splitting. "Done when" holds vacuously — there's only one channel class now, so there are no two to compare. **This is the one finding in this batch where closing it took something away rather than adding structure**; worth remembering if `C-06`/`C-07` are picked up next, since that's when Strategy actually earns its place back.

---

<a id="s-11"></a>
## S-11 · MINOR · `infra.memory` mixes test doubles with adapters

**Status:** `DONE`
**Where:** `src/main/java/notification/infra/memory/` → `AlwaysFailingProviderClient`/`FlakyProviderClient` now in `src/test/java/notification/infra/memory/`

`AlwaysFailingProviderClient` and `FlakyProviderClient` are test fixtures. `InMemoryNotificationRepository` is a legitimate dev adapter. `SimpleTemplateEngine` is arguably a real implementation. All sit on the main source path, so nothing prevents production wiring from selecting the always-failing client.

**Fix:** Move fixtures to `src/test` once `S-12` gives us that root.

**Implemented (r4):** Moved both classes to `src/test/java/notification/infra/memory/`, done together with `S-12` since there was no `src/test` root to move them into before. `BeanConfiguration` (the composition root, `S-01`'s `notification.boot`) now wires only `ReliableProviderClient` in the real app — `AlwaysFailingProviderClient`/`FlakyProviderClient` are structurally unreachable from production wiring. They weren't dead weight even so: added `NotificationWorkerRealResilienceTest`, which wires them with the *real* `ExponentialBackoffRetryPolicy`/`SimpleCircuitBreaker`/`InMemoryDeadLetterQueue` to prove the retry-then-recover and retry-then-DLQ paths work end to end — coverage the earlier Mockito-based `NotificationWorkerTest` can't provide, since it stubs the retry/breaker outcomes directly rather than exercising them for real.

---

<a id="s-12"></a>
## S-12 · MINOR · No `src/main/java` root, no build file

**Status:** `DONE`
**Where:** repository root — `pom.xml`, `src/main/java/`, `src/test/java/`

Sources sit at `notification-service/notification/...`. The project cannot be built by any standard tool, and IDE import is awkward. Blocks `S-01` and `S-11`.

**Fix:** Adopt the standard Maven/Gradle layout and add a build file.

**Implemented (r4):** Adopted Maven (not Gradle) with Spring Boot 3.5.16 as the parent POM — the user's explicit request, and the trigger for revisiting this finding at all. Standard layout: `src/main/java/notification/**` (53 files) and `src/test/java/notification/**` (14 files, including the 2 relocated from `S-11`). Added a `notification.boot` composition root (`NotificationServiceApplication` + `BeanConfiguration`) that wires the existing in-memory adapters as Spring beans via explicit `@Bean` methods — no `@Component`/`@Autowired` scattered through `domain`/`core`/`spi`/`infra`, keeping them framework-agnostic (enforced by `ArchitectureRulesTest`'s `only_boot_imports_spring` rule). Verified beyond "it compiles": `mvn test` passes 60 tests, `mvn package` produces a runnable fat jar, and the jar was actually started (`java -jar target/notification-service-0.1.0-SNAPSHOT.jar`) and confirmed it boots cleanly to `Started NotificationServiceApplication` before being killed.

---

<a id="s-13"></a>
## S-13 · MINOR · Zero `package-info.java`

**Status:** `OPEN`

The architectural rules exist only in review prose — precisely where they'll be lost. Each package should state its purpose and what it may depend on.

**Fix:** One `package-info.java` per package, each naming its allowed dependencies. Pairs with `S-01`, which makes the rules executable.

---

<a id="s-14"></a>
## S-14 · MINOR · `TenantBulkheadPool` in `core` is infrastructure

**Status:** `OPEN`
**Where:** `src/main/java/notification/core/TenantBulkheadPool.java`

Thread-pool management is not orchestration logic. `core` should depend on a `TenantExecutor` abstraction with the concrete pool supplied by infra.

**Fix:** Extract the port, move the implementation. Coordinate with `C-05`, which rewrites this class anyway.

---

# Hygiene

<a id="h-01"></a>
## H-01 · MINOR · `DEAD` does double duty

**Status:** `OPEN` · **Where:** `src/main/java/notification/core/DefaultNotificationService.java`, `src/main/java/notification/domain/DeliveryStatus.java`

"Recipient opted out" and "provider failed after retries" both land on `DEAD`. They page differently and mean opposite things operationally. Add `SUPPRESSED` for preference rejection.

<a id="h-02"></a>
## H-02 · MINOR · `DELIVERED` / `FAILED` never assigned

**Status:** `OPEN` · **Where:** `src/main/java/notification/domain/DeliveryStatus.java`

Both are declared and never set; `SENT` is terminal in practice. There is no provider-receipt/webhook path to carry `SENT → DELIVERED`. Either model the callback or drop the values.

<a id="h-03"></a>
## H-03 · MINOR · Opt-out path resolves provider config it never uses

**Status:** `OPEN` · **Where:** `src/main/java/notification/core/DefaultNotificationService.java`

The rejection branch calls `providerConfigRegistry.resolve(...)` purely to build a record that will never be sent — and throws `IllegalStateException` if the tenant has no config for a channel they've opted out of.

<a id="h-04"></a>
## H-04 · MINOR · `Map.copyOf` NPEs on null param values

**Status:** `OPEN` · **Where:** `src/main/java/notification/domain/NotificationRequest.java`

Template params legitimately carry nulls; `Map.copyOf` rejects them at construction.

<a id="h-05"></a>
## H-05 · MINOR · Missing template params render as empty string

**Status:** `OPEN` · **Where:** `src/main/java/notification/infra/memory/SimpleTemplateEngine.java`

`interpolate` substitutes `""` for absent keys, so "Your order {{orderId}} shipped" silently becomes "Your order  shipped". Fail fast on missing required params.

<a id="h-06"></a>
## H-06 · MINOR · Template/request channel mismatch unvalidated

**Status:** `OPEN` · **Where:** `src/main/java/notification/infra/memory/SimpleTemplateEngine.java`, `src/main/java/notification/core/NotificationWorker.java`

`RenderedMessage.channel` comes from the template; `channelFactory.forType()` uses `record.channel()`. Nothing checks they agree.

<a id="h-07"></a>
## H-07 · MINOR · `runInTransaction(Runnable)` is too weak

**Status:** `OPEN` · **Where:** `src/main/java/notification/spi/NotificationRepository.java`

Cannot return a value or express rollback. `Supplier<T>` is more honest — and `C-01` will need a return value.

<a id="h-08"></a>
## H-08 · MINOR · Repository error contracts are inconsistent

**Status:** `OPEN` · **Where:** `src/main/java/notification/spi/NotificationRepository.java`

`findByKey` returns `Optional`; `load` throws. Pick one convention.

<a id="h-09"></a>
## H-09 · MINOR · "Auditable" is met only by an in-memory list

**Status:** `OPEN` · **Where:** `src/main/java/notification/domain/DeliveryRecord.java`

`DeliveryAttempt` accumulates on the aggregate. There is no append-only audit trail, no actor identity, no emitted events — a stated requirement is effectively unimplemented.

<a id="h-10"></a>
## H-10 · BLOCKER · Zero tests

**Status:** `WIP`
**Where:** `src/test/java/notification/**` (14 test classes, 60 tests)

Nothing is verified. The idempotency, bulkhead, and breaker claims are exactly the ones a reviewer would challenge, and exactly the ones that need `CountDownLatch`-style concurrency tests. Severity is BLOCKER because `C-01`, `C-02`, `C-05`, `C-11` and `C-12` cannot be credibly closed without them.

**Progress (r4):** No longer zero. `mvn test` runs 60 tests: unit coverage for `DeliveryRecord`, `InMemoryNotificationRepository`, `InMemoryRateLimiter`, `SimpleTemplateEngine`, `ExponentialBackoffRetryPolicy` (including retry-exhaustion), `SimpleCircuitBreaker` (including the OPEN→HALF_OPEN→CLOSED cycle and the HALF_OPEN-failure-reopens-immediately case), `DefaultChannelFactory`/`ProviderBackedChannel`; Mockito-isolated branch tests for `DefaultNotificationService` (unknown tenant, duplicate request, opted-out recipient, throttled, happy path) and `NotificationWorker` (success, retries-exhausted, circuit-open); a real-collaborator test wiring `NotificationWorker` to the actual retry/breaker/DLQ stack (`NotificationWorkerRealResilienceTest`, see `S-11`); a `@SpringBootTest` context-loads smoke test; and a `@SpringBootTest` end-to-end test that sends through the real wired app and polls for `SENT`.

**Still open, and why this isn't `DONE`:** every test above is single-threaded. None of it is the `CountDownLatch`-style concurrency test this finding specifically calls for, because there's nothing to write yet: `C-01` (idempotency race) and `C-02` (worker not idempotent under redelivery) are the bugs such a test would need to catch, and they're still open — a concurrency test against the current code would either pass vacuously (proving nothing) or require asserting the bug's existence (fragile, and backwards — the test should pin the fix, not the defect). `C-05` (bulkhead sizing), `C-11` (breaker/retry nesting order), and `C-12` (HALF_OPEN stampede) are similarly untested for the same reason. This is exactly why `H-10` sits in Wave 2 with `C-01`/`C-02`, not Wave 1 — see "Suggested implementation order" above. Close this once those land and a concurrency test for each is added.

---

## Revision history

| Rev | Date | Change |
|---|---|---|
| r1 | 2026-08-11 | Initial implementation review — findings C-01…C-12, H-01…H-10 |
| r2 | 2026-08-11 | Package restructure applied; restructure review added — findings S-01…S-14. Corrected r2 hand-off claim that layering was compiler-enforced (see `S-01`) |
| r3 | 2026-08-11 | Implemented all 8 Structure BLOCKER/MAJOR findings: `S-01` (`ArchitectureTest` + `verify-architecture.sh`, standalone checker in place of ArchUnit — no build file in this repo), `S-02` (`DeliveryRecord.pending(...)` decoupled from `NotificationRequest`; `NotificationRequest`/`DeliveryHandle` moved to `api`), `S-03`+`S-04` (`spi` split into `spi.port`/`spi.channel`/`spi.resilience`; `channel`/`resilience` moved under `infra.*`), `S-06` (`UnknownTenantException` → `domain`, not `api`), `S-08` (`ProviderResponse` deleted, `ProviderClient` returns `DeliveryResult` directly), `S-09` (`PollableOutbox` removed; `RecordHandler` port added to `core`; `OutboxDispatcher` moved to `infra.memory`), `S-10` (4 channel classes collapsed into `ProviderBackedChannel`, deliberately deferring the real per-channel variation to `C-06`/`C-07`). 55 source files, compiles clean, `verify-architecture.sh` passes and was proven to catch a deliberate violation. No Correctness (`C-*`) or Hygiene (`H-*`) finding touched. |
| r4 | 2026-08-11 | Adopted Maven + Spring Boot 3.5.16 (user request): standard `src/main/java`/`src/test/java` layout, `pom.xml`, and a `notification.boot` composition root (`NotificationServiceApplication` + `BeanConfiguration`) wiring the existing adapters as explicit `@Bean`s — `domain`/`api`/`spi`/`core`/`infra` stay Spring-free by construction. Closed `S-12` (build file + layout) and `S-11` (test-only `FlakyProviderClient`/`AlwaysFailingProviderClient` moved to `src/test`, done together with `S-12`). Upgraded `S-01` from the standalone checker to a real `archunit-junit5` test (`ArchitectureRulesTest`, 10 rules) now that a build tool exists to run it — exactly the upgrade path the r3 note for `S-01` had already named. Added a 60-test suite (`mvn test`, all passing): unit tests for `DeliveryRecord`, the in-memory adapters, `ExponentialBackoffRetryPolicy`, `SimpleCircuitBreaker`, `DefaultChannelFactory`/`ProviderBackedChannel`; Mockito-isolated branch tests for `DefaultNotificationService` and `NotificationWorker`; a real-collaborator resilience test putting the relocated test fixtures to use; a `@SpringBootTest` context-loads test; and a `@SpringBootTest` end-to-end test exercising the real async pipeline. Verified beyond compilation: `mvn package` produces a runnable jar, and the jar was actually started and confirmed booting. Moved `H-10` from `OPEN` to `WIP` — substantial single-threaded coverage now exists, but the concurrency tests it specifically calls for are still blocked on `C-01`/`C-02` landing first (see its detail section for why testing them now would be premature, not just incomplete). No `C-*` (Correctness) finding touched. |

<!--
When closing a finding:
  1. Flip its Status in both the summary table and the detail section.
  2. Confirm the "Done when" criterion actually holds.
  3. Add a revision-history row.
-->

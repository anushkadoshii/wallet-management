# Concurrency benchmark: four overdraft strategies

**What:** `ledger-service`'s four ways to stop two payments spending the same money
(`kosh.ledger.overdraft-strategy`), measured with 1,000 × ₹1 transfers on 50 threads.

**How to run** (from `services/ledger-service`, Docker running). In PowerShell, keep the quotes around each `-D` flag:

```
./mvnw test '-Dtest=*StrategyTest' '-Dkosh.benchmark=true'
```

Copy the `BENCH` lines from the output into the table below.

**Setup:** laptop with an Intel Core i5-1335U (10 cores / 12 threads) and 16 GB RAM, Windows 11 + Docker Desktop,
Postgres 16 in Testcontainers (default settings), HikariCP pool of 10 connections, 50 worker threads,
a 300-payment warm-up thrown away before each strategy. Measured 2026-11-28.

**Scenarios:**
- **1 hot wallet:** all 1,000 payments go from ONE wallet (funded ₹1,000) to ONE shop, so every payment fights over the same 2 rows.
- **50 wallets:** payment *n* goes from wallet *n % 50* to shop *n % 50* (each wallet funded ₹20), so collisions are rare.

In both, every payment should succeed. "Gave up" = still losing after 100 attempts.

## Results

| Strategy | Scenario | Succeeded | Gave up | Payments/s | p50 ms | p95 ms | p99 ms | Retries | Total ms | Our 10 connections, on average |
|---|---|---|---|---|---|---|---|---|---|---|
| conditional | 1 hot wallet | 1000 | 0 | 226 | 213.6 | 321.1 | 396.1 | 0 | 4424 | 0.9 running, 6.1 waiting on a lock, 2.3 idle in transaction |
| conditional | 50 wallets | 1000 | 0 | 512 | 88.2 | 224.0 | 334.3 | 0 | 1954 | 0.9 running, 0.0 waiting on a lock, 7.3 idle in transaction |
| optimistic | 1 hot wallet | 1000 | 0 | 72 | 479.2 | 1976.4 | 3236.4 | 8407 | 13900 | 1.0 running, 1.3 waiting on a lock, 6.5 idle in transaction |
| optimistic | 50 wallets | 1000 | 0 | 676 | 62.8 | 174.7 | 273.2 | 29 | 1479 | 1.3 running, 0.0 waiting on a lock, 7.4 idle in transaction |
| pessimistic | 1 hot wallet | 1000 | 0 | 203 | 223.2 | 591.6 | 897.4 | 0 | 4918 | 0.3 running, 8.5 waiting on a lock, 0.9 idle in transaction |
| pessimistic | 50 wallets | 1000 | 0 | 697 | 62.8 | 174.7 | 314.8 | 0 | 1435 | 1.3 running, 0.0 waiting on a lock, 6.9 idle in transaction |
| serializable | 1 hot wallet | 999 | 1 | 56 | 171.0 | 3783.9 | 6713.4 | 8777 | 17778 | 1.0 running, 0.9 waiting on a lock, 4.7 idle in transaction |
| serializable | 50 wallets | 1000 | 0 | 485 | 85.5 | 256.1 | 366.9 | 42 | 2063 | 1.1 running, 0.0 waiting on a lock, 5.8 idle in transaction |

## What the numbers say

- **On the hot wallet, the strategies that wait beat the ones that retry.** Conditional (226/s) and pessimistic (203/s) are about **3–4× faster** than optimistic (72/s) and serializable (56/s). When everyone wants the same row, queuing for a lock costs a little waiting. Retrying throws away a whole transaction (entry insert, postings, reads, flush) and starts again: optimistic and serializable each did **~8,400–8,800 retries for 1,000 payments**, about 8–9 wasted attempts per payment.
- **Retries wreck the tail, not just the average.** Optimistic's p50 on the hot wallet is 479 ms, but its p99 is 3.2 s. Serializable's p50 looks fine (171 ms) but its p99 is **6.7 s**, because some payments kept losing over and over. Conditional's p99 is only 396 ms. Users feel the p99.
- **Serializable even gave up on 1 payment** (lost 100 times in a row). Under heavy contention a retry-based strategy can fail outright. A wait-based one can't.
- **Spread over 50 wallets, contention almost disappears and the ranking flips.** Pessimistic (697/s) and optimistic (676/s) are now slightly ahead of conditional (512/s) and serializable (485/s). Retries drop to 29–42 in total, and nobody waits on a lock (0.0). Optimistic locking is a good bet when conflicts are rare and a bad one when they are common. Conditional was also about 25% behind in my earlier two runs. I didn't dig into why; my guess (not checked) is the extra flush and clear that each native `@Modifying` update does.
- **"Waiting on a lock" vs "idle in transaction" shows where the time goes.** On the hot wallet, pessimistic leaves **8.5 of 10 connections waiting on a lock**: the row is locked from the `SELECT ... FOR UPDATE`, through the Java check and the flush, to COMMIT, so the queue is long. Conditional only locks from its first `UPDATE` to COMMIT, so 6.1 wait. Optimistic and serializable rarely wait (≈1); they collide later and retry instead. In the 50-wallet runs, 6–7 connections are **idle in transaction** on average: Postgres has a transaction open and is waiting for Java to send the next statement. Without contention, the bottleneck is the ~8 round trips per payment (Java, Hibernate and the network hop into Docker), not the database.
- **Kosh keeps `conditional` as the default.** It's fastest on hot accounts (which a wallet has: popular merchants, BANK_CLEARING on every top-up), never retries, never gives up, and is the simplest. Use `pessimistic` when the decision needs several rows or Java logic that won't fit in one UPDATE (for example daily limits). Use `optimistic` for data that is rarely edited at the same moment. Use `serializable` only when a rule spans many rows (write skew) and the retries are acceptable.

## Caveats

- **One laptop, not a server.** Windows + Docker Desktop puts Postgres inside a virtual machine, so every query pays an extra network hop. A real server would be much faster in absolute terms. These numbers are only good for **comparing the strategies with each other**.
- **Noisy.** This is one run. In two earlier runs on the same laptop the same strategy varied by up to ~25%, and other apps (browser, VS Code) compete for the CPU. The big gaps (3–4× on the hot wallet) are real; small gaps like 676 vs 697/s are not.
- **The pool caps parallelism at 10.** With HikariCP's default of 10 connections, at most 10 payments are inside Postgres at once. A bigger pool would make the hot wallet worse for optimistic and serializable (more collisions), and might help the spread case.
- **A tiny, simple workload.** Every payment is a ₹1 two-account transfer on small tables that fit in memory, with Postgres's default settings. Real traffic mixes reads, larger entries and fees.
- **Latency starts when a worker picks the payment up.** Time spent waiting in the 50-thread queue before that isn't counted, but waiting for a database connection, for locks and for retries is.

## Questions (Day 18)

**Q1. Why is the benchmark skipped in a normal build, instead of being a test like "optimistic must finish in under 20 s"?**
Speed depends on the machine and on what else is running. A time limit that passes on this laptop could fail on a slower CI runner or a busy afternoon, for no code reason. That's a *flaky* test, and flaky tests get ignored. The benchmark also takes about 2½ minutes. So it only **measures and prints**, runs only with `-Dkosh.benchmark=true` (`@EnabledIfSystemProperty`), and its one assertion is about correctness (the invariant checker), not speed.

**Q2. On the hot wallet, optimistic's p50 is ~480 ms but its p99 is ~3.2 s. What does a user notice, and why would the average hide it?**
Most users get an answer in about half a second, but **1 in 100 waits over 3 seconds**. That's the payment that kept losing the race and retrying. Those users think the app is broken. An average mixes 990 quick payments with 10 very slow ones into a middling number that describes nobody: the 10 slow ones barely move it. Percentiles show the tail directly.

**Q3. Why does `DbActivitySampler` open its own connection instead of using the pool?**
During the benchmark all 10 pool connections are in use and about 40 threads are queuing for one. A sampler that borrowed from the pool would sit in that queue and only get a connection in the quiet moments, so it would miss exactly what it's meant to see (and would also take a connection away from a payment). Its own `DriverManager` connection, outside the pool, can look at any moment.

**Q4. On the hot wallet, conditional and pessimistic have 0 retries, but optimistic has ~8,400. What is thrown away in each retry, and why is it ~3× slower?**
A retry throws away a **whole transaction**: the account check, the journal entry and its postings, the balance reads, the flush, and the rollback itself. Then it waits a random 1–9 ms and does all of it again from BEGIN. With 10 payments racing for the same row, roughly 1 wins each round and 9 lose, so most of the database's work is spent on attempts that get rolled back. Conditional and pessimistic do each payment's work **once**: they wait in a queue, then succeed.

**Q5. Pessimistic leaves ~8.5 of 10 connections waiting on a lock, conditional ~6. Why do others wait longer under pessimistic?**
Because the row stays locked longer. Pessimistic takes the lock at `SELECT ... FOR UPDATE` and keeps it while Java checks the balance, while Hibernate flushes the two UPDATEs, and until COMMIT. That's several round trips with the row held. Conditional takes the lock only at its first `UPDATE`, so the lock covers just the two UPDATEs and the COMMIT. A longer hold means a longer queue behind it, which is also why pessimistic's p99 (897 ms) is more than double conditional's (396 ms).

**Q6. In the lock lab, why does B wait on a `transactionid` lock instead of on "the alice row"? And why does C wait on a `tuple` lock held by B?**
Postgres doesn't keep row locks in `pg_locks`. When A updates alice, it writes A's transaction id into the row itself, which is how it can lock millions of rows without using memory. So B sees "this row belongs to transaction X" and waits for **transaction X to end**, by asking for a `ShareLock` on X's id (`granted = f` until A commits or rolls back). B also takes the row's `tuple` lock, which means "I'm first in line". C then wants that same tuple lock, which B holds, so C queues behind B. The line is A ← B ← C, and `pg_blocking_pids` shows exactly that chain.

**Q7. Spread over 50 wallets, why are optimistic and pessimistic as fast as conditional (or faster), while on the hot wallet they're far apart?**
The strategies only differ in what they do **when two payments collide**. With 50 pairs and only 10 payments in flight, collisions are rare (29–42 retries out of 1,000, and 0.0 waiting on a lock). Then all four do about the same work per payment, and the time is mostly round trips between Java and the database. On the hot wallet *every* payment collides, so the collision behaviour (wait vs retry) decides everything.

**Q8. Serializable gave up on a payment, and an HTTP client would get 500 "Unexpected error". What's a better answer, and how could giving up be made rarer?**
500 says "the server is broken", but nothing is broken: the payment lost too many races and was fully rolled back. A better answer is **503 Service Unavailable** with a `Retry-After` header (or 409 Conflict), with a message like "busy, please retry". It's safe for the client to retry with the **same Idempotency-Key**, because the key row was rolled back with the transfer. To give up less often:
- wait longer after each failure (exponential back-off, plus random jitter)
- allow more attempts
- limit how many payments touch one hot account at once
- or simply use conditional or pessimistic for hot accounts, which wait instead of retrying

**Q9. Which strategy should Kosh keep as its default, and when would you switch?**
**Conditional.** It's fastest on the hot wallet, has zero retries, never gives up, and is the least code. Switch to **pessimistic** when the check can't be written as one SQL WHERE (several rows, Java rules such as daily limits or fees). Switch to **optimistic** for things that are rarely changed at the same moment (it was quickest when spread out). Switch to **serializable** only when a rule covers many rows and could suffer write skew, and the retries are worth it.

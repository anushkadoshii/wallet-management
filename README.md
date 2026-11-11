# Kosh

A double-entry wallet and payments ledger, built as Java Spring Boot microservices on PostgreSQL and Kafka.

Customers top up from a (simulated) bank, pay merchants, send money to friends and withdraw. Every rupee is
accounted for: money is never a number someone overwrites; a balance is the running total of an append-only,
double-entry ledger.

> 🚧 Work in progress. Full design: [docs/design.pdf](docs/design.pdf)

## Goals

1. Every balance is correct and explainable: each figure traces back to the journal entries that produced it.
2. No wallet ever spends more than it holds, even under heavy concurrent load.
3. A retried request or a duplicated webhook never moves money twice.
4. Crashed services, bank timeouts and failed payouts never lose or create money. The books always sum to zero.
5. The ledger agrees with the bank's statement every day, and every difference is found and explained.

## Architecture (planned)

| Part | Role |
|---|---|
| `services/ledger-service` | The only writer of money: accounts, entries, postings, balances, holds |
| `services/payments-service` | Payments, transfers, refunds, payouts as sagas |
| `services/bank-connector` | Talks to the bank: top-ups, webhooks, payouts, reconciliation |
| `services/identity-service` | Users, sign-in, JWT |
| `services/api-gateway` | Single entry point, token check, correlation ID |
| `mock-bank` | Simulated bank that fails on purpose |
| `console` | React web console |

**Stack:** Java 21, Spring Boot 3, PostgreSQL 16, Kafka (KRaft), Redis, Docker Compose, Kubernetes (kind), JUnit 5, Testcontainers, k6.

## Status

- [ ] Checkpoint A: ledger-service + concurrency benchmark
- [ ] Checkpoint B: services on Kafka, outbox, sagas, chaos test
- [ ] Checkpoint C: hot-account benchmark, console, Kubernetes, write-up

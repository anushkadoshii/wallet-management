# Learning log

Three lines a day: what I learned, what broke, what I'd do differently.

## Day 1 · Nov 10: Setup + double-entry on paper
- Learned: Every money movement is one entry whose postings sum to zero. Money enters only via BANK_CLEARING (top-up) and leaves only via a completed payout. A withdrawal needs two entries because PAYOUTS_IN_FLIGHT holds the money while the bank hasn't answered yet. System accounts like BANK_CLEARING can go negative (−₹400 means ₹400 from outside is inside Kosh); wallets can't.
- Broke: Nothing major. I don't need IntelliJ/DBeaver/Bruno: VS Code's Java, Spring Boot, SQLTools and REST Client extensions cover them, and every Spring project ships its own Maven wrapper (`mvnw`).
- Next time: Push the repo to GitHub the same day, not later.

## Day 2 · Nov 10: Java for Spring (records, enums, streams, exceptions)
- Learned: Records give immutable data classes for free (good fit for postings, which must never change). Enums turn typos into compile errors. `flatMap` turns "list of entries, each with a list of postings" into one stream; `groupingBy` + `summingLong` computes every balance in one line, which is the code version of "a balance is the sum of its postings". `Math.addExact` throws on overflow instead of silently wrapping. Exceptions are objects: `throw new ...`, and nothing after a `throw` can run.
- Broke: (1) `throw IllegalArgumentException(...)` without `new` and an unreachable `return` after it: compile errors. (2) VS Code kept reporting a build failure from an old, half-typed version of `AccountType.java` even though Maven compiled fine; fixed with "Java: Clean Java Language Server Workspace". (3) Logic bug: `sum += amount` was inside the `if (amount == 0)` block, so the sum stayed 0 and the zero-sum check could never fail. It compiled and looked fine; only a deliberately broken entry would have caught it.
- Next time: For every rule I write, also write one input that should break it and check that it does (same idea as the break-it experiments). If Maven and VS Code disagree, trust `mvnw` and clean the VS Code Java workspace.

## Day 3 · Nov 11: Maven, JUnit 5, Spring dependency injection
- Learned: JUnit tests replace reading `println` output: `assertThrows(Ex.class, () -> code)` takes a lambda so JUnit can run the code inside its own try/catch, and checking the message proves it failed for the right reason. Spring DI: classes declare what they need in their constructor and Spring passes in the bean, so the controller never creates or chooses its calculator; that keeps them loosely coupled (swap implementation or pass a fake in tests without touching the controller). This works for any Spring-managed class, not just controllers. Half-up rounding in integer paise: `(amount × bps + 5000) / 10000`, with `multiplyExact` against overflow.
- Broke: (1) `assertThat` undefined: forgot its static import from `org.assertj`. (2) My first fee version added 1 for any remainder; that's ceiling rounding (1.49 → 2), which would overcharge merchants. Fix: round up only when the remainder is ≥ 5000 (half of 10,000). (3) My first explanation of DI said "because it's a RestController"; wrong reason, DI applies to any bean.
- Next time: Check rounding logic against values just below, at and above .5 before trusting it. When explaining a concept, answer "why" (what goes wrong without it), not just "how".

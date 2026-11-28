package com.kosh.ledger.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import com.kosh.ledger.config.LedgerProperties;
import com.kosh.ledger.config.OverdraftStrategy;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.invariant.InvariantChecker;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.service.AccountService;
import com.kosh.ledger.service.LedgerService;
import com.kosh.ledger.service.TransactionRunner;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The rule every strategy must pass: 1,000 ₹1 payments at once from a ₹100 wallet
 * means exactly 100 succeed. Each subclass picks a strategy with its Spring annotations.
 * Also holds the Day 18 benchmark, which only runs with -Dkosh.benchmark=true.
 */
abstract class ConcurrentPaymentsContract {

    static final int PAYMENTS = 1000;
    static final int THREADS = 50;

    @Autowired LedgerService ledger;
    @Autowired AccountService accountService;
    @Autowired BalanceRepository balances;
    @Autowired InvariantChecker checker;
    @Autowired LedgerProperties properties;
    @Autowired TransactionRunner transactions;
    @Autowired DataSource dataSource;

    /** The strategy this subclass expects to be running. */
    abstract OverdraftStrategy strategy();

    @Test
    void exactlyOneHundredOfAThousandPaymentsSucceed() throws InterruptedException {
        assertThat(properties.overdraftStrategy()).isEqualTo(strategy());   // the property really took effect

        UUID wallet = newWallet();
        UUID shop = newWallet();
        ledger.topUp("storm-" + UUID.randomUUID(), wallet, 100);
        long retriesBefore = transactions.retries();

        ConcurrencyHarness.Outcome outcome = ConcurrencyHarness.run(PAYMENTS, THREADS,
                n -> ledger.transfer("storm-" + UUID.randomUUID(), wallet, shop, 1));
        System.out.println(strategy() + ": " + outcome + ", retries=" + (transactions.retries() - retriesBefore));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.succeeded()).isEqualTo(100);
        assertThat(outcome.insufficientFunds()).isEqualTo(900);
        assertThat(balances.findById(wallet).orElseThrow().getPosted()).isZero();
        assertThat(balances.findById(shop).orElseThrow().getPosted()).isEqualTo(100);
        assertThat(checker.check()).isEmpty();
    }

    // ---------------------------------------------------------------- Day 18 benchmark

    /**
     * Not a pass/fail test: it measures. Skipped in a normal build (timings are noisy and slow);
     * run it with  ./mvnw test -Dtest='*StrategyTest' -Dkosh.benchmark=true
     */
    @Test
    @EnabledIfSystemProperty(named = "kosh.benchmark", matches = "true")
    void benchmark() throws Exception {
        measure("warm-up", 50, 300);      // JIT, connection pool, Hibernate caches; result thrown away
        String hot = measure("1 hot wallet", 1, PAYMENTS);
        String spread = measure("50 wallets", 50, PAYMENTS);
        System.out.println("BENCH " + hot);
        System.out.println("BENCH " + spread);
        assertThat(checker.check()).isEmpty();   // however slow, the books must still balance
    }

    /**
     * `payments` ₹1 transfers spread over `pairs` (wallet -> shop) pairs, every wallet funded so
     * that ALL of them should succeed. 1 pair = everyone fights over the same two rows.
     * Returns one row of the markdown table.
     */
    private String measure(String scenario, int pairs, int payments) throws Exception {
        UUID[] wallets = new UUID[pairs];
        UUID[] shops = new UUID[pairs];
        for (int p = 0; p < pairs; p++) {
            wallets[p] = newWallet();
            shops[p] = newWallet();
            ledger.topUp("bench-" + UUID.randomUUID(), wallets[p], payments / pairs);
        }
        long retriesBefore = transactions.retries();

        ConcurrencyHarness.Outcome outcome;
        DbActivitySampler.Averages db;
        try (DbActivitySampler sampler = new DbActivitySampler((HikariDataSource) dataSource)) {
            outcome = ConcurrencyHarness.run(payments, THREADS,
                    n -> ledger.transfer("bench-" + UUID.randomUUID(), wallets[n % pairs], shops[n % pairs], 1));
            db = sampler.stop();
        }
        long retries = transactions.retries() - retriesBefore;

        ConcurrencyHarness.Latency l = outcome.latency();
        return String.format("| %s | %s | %d | %d | %.0f | %.1f | %.1f | %.1f | %d | %d | %s |",
                strategy().name().toLowerCase(), scenario, outcome.succeeded(), outcome.unexpected().size(),
                outcome.perSecond(), l.p50(), l.p95(), l.p99(), retries, outcome.millis(), db);
    }

    private UUID newWallet() {
        return accountService.create("storm-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET).getId();
    }
}

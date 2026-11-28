package com.kosh.ledger.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kosh.ledger.config.LedgerProperties;
import com.kosh.ledger.config.OverdraftStrategy;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.invariant.InvariantChecker;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.service.AccountService;
import com.kosh.ledger.service.LedgerService;
import com.kosh.ledger.service.TransactionRunner;

/**
 * The rule every strategy must pass: 1,000 ₹1 payments at once from a ₹100 wallet
 * means exactly 100 succeed. Each subclass picks a strategy with its Spring annotations.
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

    /** The strategy this subclass expects to be running. */
    abstract OverdraftStrategy strategy();

    @Test
    void exactlyOneHundredOfAThousandPaymentsSucceed() throws InterruptedException {
        assertThat(properties.overdraftStrategy()).isEqualTo(strategy());   // the property really took effect

        UUID wallet = accountService.create("storm-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET).getId();
        UUID shop = accountService.create("storm-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET).getId();
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
}

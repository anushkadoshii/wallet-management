package com.kosh.ledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.kosh.ledger.TestcontainersConfiguration;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.invariant.InvariantChecker;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.repository.JournalEntryRepository;
import com.kosh.ledger.service.AccountNotFoundException;
import com.kosh.ledger.service.AccountService;
import com.kosh.ledger.service.InsufficientFundsException;
import com.kosh.ledger.service.LedgerService;

/**
 * Real Spring + a throwaway Postgres container. NOT @Transactional:
 * everything commits for real, so the deferred trigger runs.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTest {

    @Autowired LedgerService ledger;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired BalanceRepository balances;
    @Autowired JournalEntryRepository entries;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired InvariantChecker checker;

    /** Runs after EVERY test: the books must still be correct. */
    @AfterEach
    void booksStillBalance() {
        assertThat(checker.check()).isEmpty();
    }

    // ---- helpers: every test makes its own fresh wallets ----

    private UUID newWallet() {
        return accountService.create("it-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET).getId();
    }

    private UUID fundedWallet(long amount) {
        UUID id = newWallet();
        ledger.topUp("it-" + UUID.randomUUID(), id, amount);
        return id;
    }

    private long posted(UUID accountId) {
        return balances.findById(accountId).orElseThrow().getPosted();
    }

    // ---- (a) the happy path ----
    @Test
    void transferMovesMoneyAndCreatesOneBalancedEntry() {
        UUID alice = fundedWallet(1000);
        UUID bob = newWallet();

        UUID entryId = ledger.transfer("it-" + UUID.randomUUID(), alice, bob, 300);

        assertThat(posted(alice)).isEqualTo(700);
        assertThat(posted(bob)).isEqualTo(300);
        JournalEntry entry = entries.findWithPostingsById(entryId).orElseThrow();
        assertThat(entry.getPostings()).hasSize(2);
    }

    // ---- (b) not enough money: nothing at all is saved ----
    @Test
    void insufficientFundsSavesNothing() {
        UUID alice = fundedWallet(100);
        UUID bob = newWallet();
        String key = "it-" + UUID.randomUUID();

        assertThatThrownBy(() -> ledger.transfer(key, alice, bob, 500))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(posted(alice)).isEqualTo(100);
        assertThat(posted(bob)).isEqualTo(0);
        assertThat(entries.findByRequestId("transfer:" + key)).isEmpty();
    }

    // ---- (c) Day 12 trigger: history can't be edited ----
    @Test
    void triggerBlocksUpdatingAPosting() {
        UUID alice = fundedWallet(1000);
        UUID entryId = ledger.transfer("it-" + UUID.randomUUID(), alice, newWallet(), 300);

        assertThatThrownBy(() -> jdbc.update("UPDATE posting SET amount = 1 WHERE entry_id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append only");
    }

    // ---- (d) Day 12 deferred trigger: unbalanced entries fail at COMMIT ----
    @Test
    void unbalancedEntryFailsAtCommit() {
        UUID alice = newWallet();
        UUID adjustment = accounts.findByType(AccountType.ADJUSTMENT).orElseThrow().getId();
        UUID entryId = UUID.randomUUID();

        // Raw SQL goes around LedgerService's Java checks, like a bug or an attacker would.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO journal_entry (id, request_id, type, actor) VALUES (?, ?, 'ADJUSTMENT', 'test')",
                    entryId, "it-unbalanced-" + entryId);
            jdbc.update("INSERT INTO posting (entry_id, account_id, amount) VALUES (?, ?, 500)", entryId, alice);
            jdbc.update("INSERT INTO posting (entry_id, account_id, amount) VALUES (?, ?, -400)", entryId, adjustment);
        }))   // the block ends -> COMMIT -> the deferred trigger fires
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("unbalanced");

        Integer saved = jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry WHERE id = ?", Integer.class, entryId);
        assertThat(saved).isZero();
    }

    // ---- (e) prove the checker CAN fail ----
    @Test
    void checkerCatchesACorruptedBalance() {
        UUID alice = newWallet();
        jdbc.update("UPDATE balance SET posted = posted + 1 WHERE account_id = ?", alice);   // break the books
        try {
            assertThat(checker.check())
                    .isNotEmpty()
                    .anyMatch(problem -> problem.contains(alice.toString()));
        } finally {
            // Undo it even if the assertion fails, or @AfterEach and every later test fail too.
            jdbc.update("UPDATE balance SET posted = posted - 1 WHERE account_id = ?", alice);
        }
    }

    @Test
    void balanceAsOfSumsPostingsUpToThatTime() {
        UUID alice = newWallet();
        UUID bob = newWallet();
        UUID topUpId = ledger.topUp("it-" + UUID.randomUUID(), alice, 1000);
        UUID transferId = ledger.transfer("it-" + UUID.randomUUID(), alice, bob, 300);

        Instant topUpAt = ledger.getEntry(topUpId).getPostedAt();
        Instant transferAt = ledger.getEntry(transferId).getPostedAt();

        assertThat(accountService.getBalanceAsOf(alice, topUpAt.minus(1, ChronoUnit.MICROS))).isEqualTo(0);
        assertThat(accountService.getBalanceAsOf(alice, topUpAt)).isEqualTo(1000);
        assertThat(accountService.getBalanceAsOf(alice, transferAt.minus(1, ChronoUnit.MICROS))).isEqualTo(1000);
        assertThat(accountService.getBalanceAsOf(alice, transferAt)).isEqualTo(700);
        assertThat(accountService.getBalanceAsOf(alice, Instant.parse("9999-01-01T00:00:00Z"))).isEqualTo(700).isEqualTo(posted(alice));
        assertThat(accountService.getBalanceAsOf(bob, transferAt)).isEqualTo(300);
        assertThatThrownBy(() -> accountService.getBalanceAsOf(UUID.randomUUID(), Instant.now())).isInstanceOf(AccountNotFoundException.class);
    }


}

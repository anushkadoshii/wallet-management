package com.kosh.ledger.service;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.domain.Balance;
import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: real Spring + real Postgres (Compose must be running).
 * NOT @Transactional on purpose, so post() commits or rolls back for real.
 */
@SpringBootTest
class LedgerServiceRollbackTest {

    @Autowired LedgerService ledger;
    @Autowired AccountRepository accounts;
    @Autowired BalanceRepository balances;
    @Autowired JournalEntryRepository entries;
    @Autowired JdbcTemplate jdbc;

    /** Creates a fresh customer wallet with a balance row (random owner so reruns don't clash). */
    private UUID newWallet() {
        Account a = accounts.save(new Account("test-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET));
        balances.save(new Balance(a.getId(), false));
        return a.getId();
    }

    private UUID bankClearing() {
        return accounts.findByType(AccountType.BANK_CLEARING).orElseThrow().getId();
    }

    private static EntryRequest transfer(String requestId, UUID from, UUID to, long amount) {
        return new EntryRequest(requestId, EntryType.TRANSFER, null, null, null, "test", null,
                List.of(new EntryRequest.Line(from, -amount), new EntryRequest.Line(to, amount)));
    }

    private long posted(UUID accountId) {
        return balances.findById(accountId).orElseThrow().getPosted();
    }

    @Test
    void movesMoneyWhenThereIsEnough() {
        UUID asha = newWallet();
        UUID ravi = newWallet();
        ledger.post(new EntryRequest("topup-" + UUID.randomUUID(), EntryType.TOP_UP, null, null, null, "test", null,
                List.of(new EntryRequest.Line(bankClearing(), -1000), new EntryRequest.Line(asha, 1000))));

        ledger.post(transfer("transfer-" + UUID.randomUUID(), asha, ravi, 400));

        assertThat(posted(asha)).isEqualTo(600);
        assertThat(posted(ravi)).isEqualTo(400);
    }

    @Test
    void tooBigDebitLeavesNothingBehind() {
        UUID asha = newWallet();
        UUID ravi = newWallet();
        ledger.post(new EntryRequest("topup-" + UUID.randomUUID(), EntryType.TOP_UP, null, null, null, "test", null,
                List.of(new EntryRequest.Line(bankClearing(), -300), new EntryRequest.Line(asha, 300))));
        String requestId = "transfer-" + UUID.randomUUID();

        assertThatThrownBy(() -> ledger.post(transfer(requestId, asha, ravi, 500)))
                .isInstanceOf(InsufficientFundsException.class);

        // no entry, no postings, balances unchanged
        assertThat(entries.findByRequestId(requestId)).isEmpty();
        Integer postingsForRavi = jdbc.queryForObject(
                "SELECT count(*) FROM posting WHERE account_id = ?", Integer.class, ravi);
        assertThat(postingsForRavi).isZero();
        assertThat(posted(asha)).isEqualTo(300);
        assertThat(posted(ravi)).isZero();
    }
}

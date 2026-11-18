package com.kosh.ledger.service;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountStatus;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.repository.JournalEntryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final BalanceRepository balances;
    private final JournalEntryRepository entries;

    // constructor injection. Spring sees this constructor and passes in the repositories.
    public LedgerService(AccountRepository accounts,
                         BalanceRepository balances,
                         JournalEntryRepository entries) {
        this.accounts = accounts;
        this.balances = balances;
        this.entries = entries;
    }

    @Transactional
    public UUID post(EntryRequest req) {
        //Check the request itself. No database yet, so it's cheap.
        validate(req);

        // combine lines per account. A TreeMap keeps its keys SORTED, so looping over it later gives ascending account-id order for free.
        Map<UUID, Long> netByAccount = new TreeMap<>();
        for (EntryRequest.Line line : req.lines()) {
            netByAccount.merge(line.accountId(), line.amount(), Long::sum);
        }

        // Load all accounts in one query and check them.
        checkAccounts(netByAccount);

        // Save the entry and its postings (the ORIGINAL lines, not the combined ones).
        JournalEntry entry = new JournalEntry(
                req.requestId(), req.type(), req.referenceType(), req.referenceId(),
                req.description(), req.actor(), req.correlationId());
        for (EntryRequest.Line line : req.lines()) {
            entry.addPosting(line.accountId(), line.amount());
        }
        entries.save(entry);   // cascade = PERSIST saves the postings too

        // Balances last, in ascending id order. Hot rows stay locked for the shortest time.
        for (Map.Entry<UUID, Long> e : netByAccount.entrySet()) {
            UUID accountId = e.getKey();
            long net = e.getValue();
            if (net < 0) {
                long amount = -net;                       // debit methods take a positive amount
                if (balances.debit(accountId, amount) == 0) {
                    throw new InsufficientFundsException(accountId, amount);   // → rollback
                }
            } else if (net > 0) {
                if (balances.credit(accountId, net) == 0) {
                    // Every account gets a balance row, so this means a bug, not a user error.
                    throw new IllegalStateException("No balance row for account " + accountId);
                }
            }
            // net == 0: the lines cancel out for this account, so there's nothing to update
        }

        return entry.getId();
    }

    private void validate(EntryRequest req) {
        // Annotations don't run when post() is called directly, so check here too.
        if (req.requestId() == null || req.requestId().isBlank()) {
            throw new InvalidEntryException("requestId is required");
        }
        if (req.type() == null) {
            throw new InvalidEntryException("type is required");
        }
        if (req.actor() == null || req.actor().isBlank()) {
            throw new InvalidEntryException("actor is required");
        }
        List<EntryRequest.Line> lines = req.lines();
        if (lines == null || lines.size() < 2) {
            throw new InvalidEntryException("An entry needs at least 2 lines");
        }
        long sum = 0;
        for (EntryRequest.Line line : lines) {
            if (line.accountId() == null) {
                throw new InvalidEntryException("Every line needs an accountId");
            }
            if (line.amount() == 0) {
                throw new InvalidEntryException("Line amounts must not be zero");
            }
            sum = Math.addExact(sum, line.amount());   // throws if the sum overflows a long
        }
        if (sum != 0) {
            throw new InvalidEntryException("Lines sum to " + sum + ", expected 0");
        }
    }

    private void checkAccounts(Map<UUID, Long> netByAccount) {
        // one query for all ids, then put the results in a map by id.
        Map<UUID, Account> found = new HashMap<>();
        for (Account a : accounts.findAllById(netByAccount.keySet())) {
            found.put(a.getId(), a);
        }

        for (Map.Entry<UUID, Long> e : netByAccount.entrySet()) {
            UUID id = e.getKey();
            Account account = found.get(id);
            if (account == null) {
                throw new AccountNotFoundException(id);
            }
            // CLOSED can't take part at all, and only ACTIVE accounts can send.
            boolean sending = e.getValue() < 0;
            if (account.getStatus() == AccountStatus.CLOSED
                    || (sending && account.getStatus() != AccountStatus.ACTIVE)) {
                throw new AccountNotActiveException(id, account.getStatus());
            }
        }
    }

    @Transactional 
    public UUID transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, long amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidEntryException("Cannot transfer to same account");
        }
        EntryRequest req = new EntryRequest(
            "transfer:" + idempotencyKey,
            EntryType.TRANSFER,
            null,
            null,
            null,
            "api",
            null,
            List.of( 
                new EntryRequest.Line(fromAccountId, -amount), 
                new EntryRequest.Line(toAccountId, amount)));
        return post(req);
    }

    @Transactional 
    public UUID topUp(String idempotencyKey, UUID walletId, long amount) {
        Account bankClearing = accounts.findByType(AccountType.BANK_CLEARING)
                .orElseThrow(() -> new IllegalStateException("BANK_CLEARING account missing"));
        EntryRequest req = new EntryRequest(
            "topup:" + idempotencyKey,
            EntryType.TOP_UP,
            null, 
            null, 
            null, 
            "dev", 
            null, 
            List.of(
                new EntryRequest.Line(bankClearing.getId(), -amount),
                new EntryRequest.Line(walletId, amount)));
        return post(req);
    }

    @Transactional(readOnly = true)
    public JournalEntry getEntry(UUID entryId) {
        return entries.findWithPostingsById(entryId)
                .orElseThrow(() -> new EntryNotFoundException(entryId));
    }
}

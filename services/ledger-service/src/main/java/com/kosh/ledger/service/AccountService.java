package com.kosh.ledger.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountStatus;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.domain.Balance;
import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.Posting;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.repository.PostingRepository;

@Service 
public class AccountService {

    private static final Instant START = Instant.EPOCH;
    private static final Instant END = Instant.parse("9999-12-31T00:00:00Z");
    private static final Sort SORT = Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("id"));
    
    private final AccountRepository accounts;
    private final BalanceRepository balances;
    private final PostingRepository postings;

    public AccountService(AccountRepository accounts, BalanceRepository balances, PostingRepository postings) {
        this.accounts = accounts;
        this.balances = balances;
        this.postings = postings;
    }

    @Transactional 
    public Account create(String ownerId, AccountType type) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new InvalidAccountException("ownerId is required");
        }
        if (type == null) {
            throw new InvalidAccountException("type is required");
        }
        if (type != AccountType.CUSTOMER_WALLET && type != AccountType.MERCHANT) {
            throw new InvalidAccountException("Account of type " + type.toString() + " can not be created");
        }
        Account account = new Account(ownerId, type);
        accounts.save(account);
        Balance balance = new Balance(account.getId(), false);
        balances.save(balance);
        return account;
    }

    @Transactional(readOnly = true)
    public Balance getBalance(UUID accountId) {
        return balances.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public Page<Posting> getStatement(UUID accountId, Instant from, Instant to, EntryType type, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        if (from == null) from = START;
        if (to == null) to = END;

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SORT);
        return postings.findStatement(accountId, from, to, type, sorted);
    }

    @Transactional 
    public Account updateStatus(UUID accountId, AccountStatus newStatus) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        account.changeStatus(newStatus);
        return account;
    }
}

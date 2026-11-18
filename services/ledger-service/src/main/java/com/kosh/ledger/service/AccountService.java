package com.kosh.ledger.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.domain.Balance;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;

@Service 
public class AccountService {
    
    private final AccountRepository accounts;
    private final BalanceRepository balances;

    public AccountService(AccountRepository accounts, BalanceRepository balances) {
        this.accounts = accounts;
        this.balances = balances;
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
}

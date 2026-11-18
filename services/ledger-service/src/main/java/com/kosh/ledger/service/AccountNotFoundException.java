package com.kosh.ledger.service;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException{
    
    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " not found");
        this.accountId = accountId;
    }

    public UUID getAccountId() { return accountId;}
}

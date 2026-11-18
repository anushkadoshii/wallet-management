package com.kosh.ledger.service;

import java.util.UUID;

import com.kosh.ledger.domain.AccountStatus;

public class AccountNotActiveException extends RuntimeException {

    private final UUID accountId;
    private final AccountStatus status;

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super("Account " + accountId + " is " + status);
        this.accountId = accountId;
        this.status = status;
    }

    public UUID getAccountId() {return accountId;}
    public AccountStatus geStatus() {return status;}
    
}

package com.kosh.ledger.service;

import java.util.UUID;

/** A debit would take a no-overdraft account below zero. Unchecked → @Transactional rolls back. */
public class InsufficientFundsException extends RuntimeException {
    
    private final UUID accountId;
    private final long amount;

    public InsufficientFundsException(UUID accountId, long amount) {
        super("Account: " + accountId + " does not have " + amount + " available");
        this.accountId = accountId;
        this.amount = amount;
    }

    public UUID getAccountId() { return accountId;}
    public long getAmount() { return amount;}
}

package com.kosh.ledger.web;

import java.time.Instant;
import java.util.UUID;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountStatus;
import com.kosh.ledger.domain.AccountType;

public record AccountResponse(UUID id, String ownerId, AccountType type, AccountStatus status, Instant createdAt) {
    
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getOwnerId(), a.getType(), a.getStatus(), a.getCreatedAt());
    }
}

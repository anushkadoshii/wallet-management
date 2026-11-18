package com.kosh.ledger.web;

import java.util.UUID;

import com.kosh.ledger.domain.Balance;

public record BalanceResponse(UUID accountId, long posted, long held, long available) {
    
    public static BalanceResponse from(Balance b) {
        return new BalanceResponse(b.getAccountId(), b.getPosted(), b.getHeld(), b.getAvailable());
    }
}

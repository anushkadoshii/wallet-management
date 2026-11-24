package com.kosh.ledger.web;

import java.time.Instant;
import java.util.UUID;

public record BalanceAsOfResponse(UUID accountId, Instant at, long posted) {
    
}

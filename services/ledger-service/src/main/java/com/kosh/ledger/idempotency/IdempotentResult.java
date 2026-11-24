package com.kosh.ledger.idempotency;

public record IdempotentResult<T> (T body, boolean replayed) {
    
}

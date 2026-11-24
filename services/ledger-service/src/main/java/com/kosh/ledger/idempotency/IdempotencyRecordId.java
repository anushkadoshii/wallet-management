package com.kosh.ledger.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable 
public record IdempotencyRecordId(
    @Column(name = "user_id") String userId,
    @Column(name = "key") String key) {
    
}

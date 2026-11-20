package com.kosh.ledger.web;

import java.time.Instant;
import java.util.UUID;

import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.Posting;

public record StatementLineResponse(
    UUID postingId,
    UUID entryId,
    EntryType entryType,
    long amount,
    Instant postedAt,
    String description) {
    
        public static StatementLineResponse from(Posting p) {
            return new StatementLineResponse(p.getId(), p.getEntry().getId(), p.getEntry().getType(), p.getAmount(), p.getPostedAt(), p.getEntry().getDescription());
        }
}

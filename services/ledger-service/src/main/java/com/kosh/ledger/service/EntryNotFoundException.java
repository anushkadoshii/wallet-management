package com.kosh.ledger.service;

import java.util.UUID;

public class EntryNotFoundException extends RuntimeException {
    
    private final UUID entryId;

    public EntryNotFoundException(UUID entryId) {
        super("Journal Entry " + entryId + " not found");
        this.entryId = entryId;
    }

    public UUID getEntryId() { return entryId; }
}

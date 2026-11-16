package com.kosh.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kosh.ledger.domain.JournalEntry;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    // Idempotency: "did I already process this request?"
    // To prevent repetition if a request is already processed
    Optional<JournalEntry> findByRequestId(String requestId);
}

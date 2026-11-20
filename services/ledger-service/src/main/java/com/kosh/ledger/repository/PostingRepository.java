package com.kosh.ledger.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.Posting;

import java.time.Instant;


public interface PostingRepository extends JpaRepository<Posting, UUID>{
    @EntityGraph(attributePaths = "entry")
    @Query("""
        SELECT p FROM Posting p JOIN p.entry e
        WHERE p.accountId = :accountId
        AND p.postedAt >= :from AND p.postedAt < :to
        AND (:type IS NULL OR e.type = :type) """)
    Page<Posting> findStatement(UUID accountId, Instant from, Instant to, EntryType type, Pageable pageable);
}

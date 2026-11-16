package com.kosh.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;

@Entity 
@Immutable 
@Table(name = "posting")
public class Posting {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected Posting() {}

    Posting(JournalEntry entry, UUID accountId, long amount) {
        if (amount == 0) {
            throw new IllegalArgumentException("Posting amount must not be zero");
        }
        this.entry = entry;
        this.accountId = accountId;
        this.amount = amount;
        this.postedAt = entry.getPostedAt();
    }

    public UUID getId() {return id;}
    public JournalEntry getEntry() {return entry;}
    public UUID getAccountId() {return accountId;}
    public long getAmount() {return amount;}
    public Instant getPostedAt() {return postedAt;}
}

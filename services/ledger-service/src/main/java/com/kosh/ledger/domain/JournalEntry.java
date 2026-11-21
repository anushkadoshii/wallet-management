package com.kosh.ledger.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;

@Entity 
@Immutable 
@Table(name = "journal_entry")
public class JournalEntry {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "description")
    private String description;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST)
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {}

    public JournalEntry(String requestId, EntryType type, String referenceType, String referenceId, String description, String actor, String correlationId) {
        this.requestId = requestId;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.actor = actor;
        this.correlationId = correlationId;
        this.postedAt = Instant.now();
    }

    public void addPosting(UUID accountId, long amount) {
        postings.add(new Posting(this, accountId, amount));
    }

    public UUID getId() {return id;}
    public String getRequestId() {return requestId;}
    public EntryType getType() {return type;}
    public String getReferenceType() {return referenceType;}
    public String getReferenceId() {return referenceId;}
    public String getDescription() {return description;}
    public String getActor() {return actor;}
    public String getCorrelationId() {return correlationId;}
    public Instant getPostedAt() {return postedAt;}
    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }
}

package com.kosh.ledger.idempotency;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity 
@Table(name = "idempotency_key")
public class IdempotencyRecord {
    
    @EmbeddedId 
    private IdempotencyRecordId id;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", nullable = false)
    private String response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(IdempotencyRecordId id, String requestHash, String response) {
        this.id = id;
        this.requestHash = requestHash;
        this.response = response;
        this.createdAt = Instant.now();
    }

    public IdempotencyRecordId getId() {return id;}
    public String getRequestHash() {return requestHash;}
    public String getResponse() {return response;}
    public Instant getCreatedAt() {return createdAt;}
}

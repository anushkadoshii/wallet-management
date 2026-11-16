package com.kosh.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity 
@Table(name = "account")
public class Account {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id")
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated (EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(String ownerId, AccountType type) {
        this.ownerId = ownerId;
        this.type = type;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return this.id; }
    public String getOwnerId() { return this.ownerId; }
    public AccountType getType() { return this.type; }
    public AccountStatus getStatus() { return this.status; }
    public Instant getCreatedAt() { return this.createdAt; }

}

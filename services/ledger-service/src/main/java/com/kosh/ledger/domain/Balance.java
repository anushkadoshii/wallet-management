package com.kosh.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity 
@Table(name = "balance")
public class Balance {
    
    @Id 
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private long posted;

    @Column(nullable = false)
    private long held;

    @Column(name = "overdraft_allowed", nullable = false)
    private boolean overdraftAllowed;

    @Column(nullable = false)
    @Version 
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Balance() {}

    public Balance(UUID accountId, boolean overdraftAllowed) {
        this.accountId = accountId;
        this.overdraftAllowed = overdraftAllowed;
        this.updatedAt = Instant.now();
    }

    public void applyNet(long net) {
        this.posted += net;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() { return accountId; }
    public long getPosted() { return posted; }
    public long getHeld() { return held; }
    public long getAvailable() { return posted - held; }  
    public boolean isOverdraftAllowed() { return overdraftAllowed; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}

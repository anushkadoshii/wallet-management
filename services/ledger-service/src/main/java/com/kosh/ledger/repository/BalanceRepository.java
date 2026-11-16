package com.kosh.ledger.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kosh.ledger.domain.Balance;

public interface BalanceRepository extends JpaRepository<Balance, UUID> {
    
}

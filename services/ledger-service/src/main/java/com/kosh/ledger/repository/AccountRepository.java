package com.kosh.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountType;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    // For system accounts only: "give me THE BANK_CLEARING account"
    // Optional gives 0 or 1 results. If we needed more than 1, use List<Account>.
    // Here, if we do findByType(CUSTOMER_WALLET) it throws exception because multiple accounts.
    Optional<Account> findByType(AccountType type); 
}

package com.kosh.ledger.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kosh.ledger.domain.Balance;

import jakarta.persistence.LockModeType;


public interface BalanceRepository extends JpaRepository<Balance, UUID> {
    
    // @Modifying is reqd for any UPDATE OR DELETE query
    // flushAutomatically = true : Before running the update, send JPA’s pending inserts (your entry and postings) to the database first.
    //clearAutomatically = true : After the update, throw away JPA’s cached objects, so nothing stale is read later.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                UPDATE balance
                SET posted = posted - :amount, version = version + 1, updated_at = now()
                WHERE account_id = :accountId
                AND (overdraft_allowed OR posted - held >= :amount)
                """, nativeQuery = true) 
    int debit(@Param("accountId") UUID accountId, @Param("amount") long amount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                UPDATE balance
                SET posted = posted + :amount, version = version + 1, updated_at = now()
                WHERE account_id = :accountId
                """, nativeQuery = true) 
    int credit(@Param("accountId") UUID accountId, @Param("amount") long amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT b FROM Balance b
        WHERE b.accountId IN :ids 
        ORDER BY b.accountId""")
    List<Balance> findAllForUpdate(@Param("ids") Collection<UUID> ids);
}

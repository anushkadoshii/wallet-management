package com.kosh.ledger.service;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kosh.ledger.config.LedgerProperties;
import com.kosh.ledger.config.OverdraftStrategy;

/**
 * Runs work in a transaction and re-runs the WHOLE transaction when it lost a race
 * (optimistic version clash, SQLSTATE 40001 serialization failure, 40P01 deadlock).
 * Only the outermost caller retries: inside an existing transaction it just runs the work.
 */

@Component 
public class TransactionRunner {

    static final int MAX_ATTEMPTS = 100;

    private final TransactionTemplate template;
    private final AtomicLong retries = new AtomicLong();

    public TransactionRunner(PlatformTransactionManager transactionManager, LedgerProperties properties) {
        this.template = new TransactionTemplate(transactionManager);
        if (properties.overdraftStrategy() == OverdraftStrategy.SERIALIZABLE) {
            this.template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        }
    }

    public <T> T inTransaction(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        for (int attempt=1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) throw e;
            }
            retries.incrementAndGet();
            backOff();
        }
        
    }

    public long retries() {
        return retries.get();
    }

    static boolean isRetryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConcurrencyFailureException) {
                return true;   // Spring's name for "lost a race": optimistic clash, lock failure
            }
            if (t instanceof SQLException sql
                    && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                return true;   // serialization failure / deadlock, however it was wrapped
            }
        }
        return false;
    }

    private static void backOff() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));   // random, so retries don't collide again
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}

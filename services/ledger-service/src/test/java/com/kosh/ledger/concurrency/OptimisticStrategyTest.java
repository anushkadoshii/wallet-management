package com.kosh.ledger.concurrency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.kosh.ledger.TestcontainersConfiguration;
import com.kosh.ledger.config.OverdraftStrategy;

@SpringBootTest(properties = "kosh.ledger.overdraft-strategy=optimistic")
@Import(TestcontainersConfiguration.class)
class OptimisticStrategyTest extends ConcurrentPaymentsContract {

    @Override
    OverdraftStrategy strategy() {
        return OverdraftStrategy.OPTIMISTIC;
    }
}

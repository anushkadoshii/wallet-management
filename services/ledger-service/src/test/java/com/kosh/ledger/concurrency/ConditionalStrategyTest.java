package com.kosh.ledger.concurrency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.kosh.ledger.TestcontainersConfiguration;
import com.kosh.ledger.config.OverdraftStrategy;

@SpringBootTest   // no property: conditional is the default in application.properties
@Import(TestcontainersConfiguration.class)
class ConditionalStrategyTest extends ConcurrentPaymentsContract {

    @Override
    OverdraftStrategy strategy() {
        return OverdraftStrategy.CONDITIONAL;
    }
}

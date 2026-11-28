package com.kosh.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kosh.ledger")
public record LedgerProperties(
    @DefaultValue("conditional") OverdraftStrategy overdraftStrategy) {
    
}

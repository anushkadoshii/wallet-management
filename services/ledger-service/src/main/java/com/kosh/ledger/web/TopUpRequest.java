package com.kosh.ledger.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TopUpRequest(
    @NotNull UUID accountId,
    @Positive long amount) {
    
}

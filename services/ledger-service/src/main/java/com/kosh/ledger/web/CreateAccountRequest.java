package com.kosh.ledger.web;

import com.kosh.ledger.domain.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotBlank String ownerId, @NotNull AccountType type) {
    
}

package com.kosh.ledger.web;

import com.kosh.ledger.domain.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(@NotNull AccountStatus status) {
    
}

package com.kosh.ledger.service;

import java.util.List;
import java.util.UUID;

import com.kosh.ledger.domain.EntryType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EntryRequest(
    @NotBlank String requestId,
    @NotNull EntryType type,
    String referenceType,
    String referenceId,
    String description,
    @NotBlank String actor,
    String correlationId,
    @NotNull @Size(min = 2) List<@Valid Line> lines) {

        public EntryRequest {
            lines = (lines == null) ? null : List.copyOf(lines);
        }

        public record Line(@NotNull UUID accountId, long amount) {
        }
    
}

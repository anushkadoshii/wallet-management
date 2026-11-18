package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.service.LedgerService;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@RestController 
@RequestMapping("/api/v1/transfers")
public class TransferController {
    
    private final LedgerService ledgerService;

    public TransferController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<EntryResponse> transfer(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody TransferRequest transferRequest) {
        
        UUID id = ledgerService.transfer(idempotencyKey, transferRequest.fromAccountId(), transferRequest.toAccountId(), transferRequest.amount());

        JournalEntry entry = ledgerService.getEntry(id);
        EntryResponse response = EntryResponse.from(entry);

        return ResponseEntity.created(URI.create("/api/v1/entries/" + response.id())).body(response);
    }
    
}

package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.service.LedgerService;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Profile("dev")
@RestController 
@RequestMapping("/dev/top-ups")
public class DevTopUpController {
    
    private final LedgerService ledgerService;

    public DevTopUpController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<EntryResponse> topUp(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody TopUpRequest req) {
        
        UUID id = ledgerService.topUp(idempotencyKey, req.accountId(), req.amount());
        JournalEntry entry = ledgerService.getEntry(id);
        return ResponseEntity.created(URI.create("/api/v1/entries/" + entry.getId())).body(EntryResponse.from(entry));
    }
    
}

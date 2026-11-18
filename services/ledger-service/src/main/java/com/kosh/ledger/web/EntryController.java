package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.service.LedgerService;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController 
@RequestMapping("/api/v1/entries")
public class EntryController {
    
    private final LedgerService ledgerService;

    public EntryController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{id}")
    public EntryResponse get(@PathVariable UUID id) {
        JournalEntry entry = ledgerService.getEntry(id);
        EntryResponse entryResponse = EntryResponse.from(entry);
        return entryResponse;
    }
    
}

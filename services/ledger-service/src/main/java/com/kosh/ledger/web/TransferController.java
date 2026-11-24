package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.idempotency.IdempotencyService;
import com.kosh.ledger.idempotency.IdempotentResult;
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
    private final IdempotencyService idempotency;

    public TransferController(LedgerService ledgerService, IdempotencyService idempotency) {
        this.ledgerService = ledgerService;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<EntryResponse> transfer(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody TransferRequest transferRequest) {

        IdempotentResult<EntryResponse> result = idempotency.execute(idempotencyKey, transferRequest, EntryResponse.class, () -> {
            UUID id = ledgerService.transfer(idempotencyKey, transferRequest.fromAccountId(), transferRequest.toAccountId(), transferRequest.amount());
            return EntryResponse.from(ledgerService.getEntry(id));
        });

        EntryResponse body = result.body();
        ResponseEntity.BodyBuilder builder = ResponseEntity.created(URI.create("/api/v1/entries/" + body.id()));
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(body);
    }
    
}

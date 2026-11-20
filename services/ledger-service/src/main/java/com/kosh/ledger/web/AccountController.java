package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.Balance;
import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.Posting;
import com.kosh.ledger.service.AccountService;

import jakarta.validation.Valid;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;




@RestController 
@RequestMapping("/api/v1/accounts")
public class AccountController {
    
    private final AccountService accountService;

    public AccountController(AccountService service) {
        this.accountService = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest req) {
        Account account = accountService.create(req.ownerId(), req.type());
        AccountResponse accountResponse = AccountResponse.from(account);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + accountResponse.id())).body(accountResponse);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        Balance bal = accountService.getBalance(id);
        BalanceResponse balanceResponse = BalanceResponse.from(bal);
        return balanceResponse;
    }
    
    @GetMapping("/{id}/statement")
    public StatementResponse statement(
        @PathVariable UUID id,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(required = false) EntryType type,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        
        Page<Posting> statement = accountService.getStatement(id, from, to, type, pageable);
        return StatementResponse.from(statement);
    }

    @PatchMapping("/{id}")
    public AccountResponse updateStatus(@PathVariable UUID id, @RequestBody @Valid UpdateAccountRequest req) {
        Account account = accountService.updateStatus(id, req.status());
        return AccountResponse.from(account);
    }
    
}

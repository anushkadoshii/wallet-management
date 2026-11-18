package com.kosh.ledger.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.Balance;
import com.kosh.ledger.service.AccountService;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



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
    
    
}

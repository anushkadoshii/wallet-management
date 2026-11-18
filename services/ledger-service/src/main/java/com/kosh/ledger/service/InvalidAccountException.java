package com.kosh.ledger.service;

public class InvalidAccountException extends RuntimeException {
    
    public InvalidAccountException(String message) {
        super(message);
    }
}

package com.kosh.ledger.service;

/** The request itself is malformed: too few lines, a zero amount, or lines that don't sum to zero. */
public class InvalidEntryException extends RuntimeException {

    public InvalidEntryException(String message) {
        super(message);
    }
    
}

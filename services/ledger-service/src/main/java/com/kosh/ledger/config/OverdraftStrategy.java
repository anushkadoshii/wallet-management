package com.kosh.ledger.config;

public enum OverdraftStrategy {
    CONDITIONAL, // UPDATE ..... SET 
    PESSIMISTIC, // SELECT .... FOR UPDATE
    OPTIMISTIC, // SELECT, check in Java, UPDATE ... WHERE version=?; retry if someone else won
    SERIALIZABLE // SELECT, check in Java, at SERIALIZABLE isolation; retry on SQLSTATE 40001.
}

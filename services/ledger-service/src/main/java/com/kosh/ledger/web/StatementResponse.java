package com.kosh.ledger.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;

import com.kosh.ledger.domain.Posting;

public record StatementResponse(
    List<StatementLineResponse> lines,
    int page,
    int size,
    long totalElements,
    int totalPages) {
    
        public static StatementResponse from(Page<Posting> page) {
            List<StatementLineResponse> lines = new ArrayList<>();
            for (Posting p: page.getContent()) {
                lines.add(StatementLineResponse.from(p));
            }
            return new StatementResponse(lines, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
}

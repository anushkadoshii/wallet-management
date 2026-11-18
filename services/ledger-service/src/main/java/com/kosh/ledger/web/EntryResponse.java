package com.kosh.ledger.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.domain.JournalEntry;
import com.kosh.ledger.domain.Posting;

public record EntryResponse(
    UUID id,
    String requestId,
    EntryType type,
    String referenceType,
    String referenceId,
    String description,
    String actor,
    Instant postedAt,
    List<PostingResponse> postings) {
    

    public record PostingResponse(UUID accountId, long amount) {
    }

    public static EntryResponse from(JournalEntry e) {
        List<PostingResponse> postingsList = new ArrayList<>();
        for (Posting p: e.getPostings()) {
            postingsList.add(new PostingResponse(p.getAccountId(), p.getAmount()));
        }

        return new EntryResponse(e.getId(), e.getRequestId(), e.getType(), e.getReferenceType(), e.getReferenceId(), e.getDescription(), e.getActor(), e.getPostedAt(), postingsList);
    }

}

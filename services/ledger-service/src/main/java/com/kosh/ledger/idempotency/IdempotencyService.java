package com.kosh.ledger.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service 
public class IdempotencyService {
    
    private static final String USER_ID = "anonymous";
    private final IdempotencyRecordRepository records;
    private final JsonMapper json;

    public IdempotencyService(IdempotencyRecordRepository records, JsonMapper json) {
        this.records = records;
        this.json = json;
    }

    @Transactional  
    public <T> IdempotentResult<T> execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        IdempotencyRecordId id = new IdempotencyRecordId(USER_ID, key);
        String hash = sha256(json.writeValueAsString(request));
        Optional<IdempotencyRecord> existing = records.findById(id);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return new IdempotentResult<>(json.readValue(record.getResponse(), responseType), true);
        }

        T response = action.get();
        records.save(new IdempotencyRecord(id, hash, json.writeValueAsString(response)));
        return new IdempotentResult<>(response, false);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}

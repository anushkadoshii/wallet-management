package com.kosh.ledger.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.kosh.ledger.TestcontainersConfiguration;
import com.kosh.ledger.domain.AccountType;
import com.kosh.ledger.invariant.InvariantChecker;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.service.AccountService;
import com.kosh.ledger.service.LedgerService;
import com.kosh.ledger.web.EntryResponse;

import tools.jackson.databind.json.JsonMapper;

/** POST /transfers over (fake) HTTP: the controller, the error handler and the database together. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdempotencyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired LedgerService ledger;
    @Autowired AccountService accountService;
    @Autowired BalanceRepository balances;
    @Autowired JdbcTemplate jdbc;
    @Autowired InvariantChecker checker;

    @AfterEach
    void booksStillBalance() {
        assertThat(checker.check()).isEmpty();
    }

    // ---- helpers (same as LedgerIntegrationTest, plus 3 new ones) ----

    private UUID newWallet() {
        return accountService.create("it-" + UUID.randomUUID(), AccountType.CUSTOMER_WALLET).getId();
    }

    private UUID fundedWallet(long amount) {
        UUID id = newWallet();
        ledger.topUp("it-" + UUID.randomUUID(), id, amount);
        return id;
    }

    private long posted(UUID accountId) {
        return balances.findById(accountId).orElseThrow().getPosted();
    }

    /** Sends POST /api/v1/transfers with the given key and body. Chain .andExpect(...) on the result. */
    private ResultActions transfer(String key, UUID from, UUID to, long amount) throws Exception {
        String body = """
                {"fromAccountId": "%s", "toAccountId": "%s", "amount": %d}
                """.formatted(from, to, amount);
        return mvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Reads the response body back into an EntryResponse. */
    private EntryResponse entryFrom(ResultActions result) throws Exception {
        return json.readValue(result.andReturn().getResponse().getContentAsString(), EntryResponse.class);
    }

    /** How many idempotency_key rows have this key. */
    private int keyRows(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_key WHERE key = ?", Integer.class, key);
    }

    // ---- tests ----

    @Test
    void sameKeySameBodyReplaysTheFirstResponse() throws Exception {
        UUID alice = fundedWallet(1000);
        UUID bob = newWallet();
        String key = UUID.randomUUID().toString();

        ResultActions first = transfer(key, alice, bob, 300)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        ResultActions second = transfer(key, alice, bob, 300)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(entryFrom(second).id()).isEqualTo(entryFrom(first).id());
        assertThat(posted(alice)).isEqualTo(700);
        assertThat(posted(bob)).isEqualTo(300);
        assertThat(keyRows(key)).isEqualTo(1);

        String storedId = jdbc.queryForObject(
                "SELECT response ->> 'id' FROM idempotency_key WHERE key = ?", String.class, key);
        assertThat(storedId).isEqualTo(entryFrom(first).id().toString());
    }

    @Test
    void sameKeyDifferentBodyIs422() throws Exception {
        UUID alice = fundedWallet(1000);
        UUID bob = newWallet();
        String key = UUID.randomUUID().toString();

        transfer(key, alice, bob, 300).andExpect(status().isCreated());
        transfer(key, alice, bob, 500).andExpect(status().isUnprocessableContent());

        assertThat(posted(alice)).isEqualTo(700);
        assertThat(posted(bob)).isEqualTo(300);
    }

    @Test
    void failedTransferStoresNoKeySoARetryCanSucceed() throws Exception {
        UUID alice = fundedWallet(100);
        UUID bob = newWallet();
        String key = UUID.randomUUID().toString();

        transfer(key, alice, bob, 500).andExpect(status().isUnprocessableContent());
        assertThat(keyRows(key)).isZero();

        ledger.topUp("it-" + UUID.randomUUID(), alice, 1000);
        transfer(key, alice, bob, 500)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(posted(alice)).isEqualTo(600);
        assertThat(posted(bob)).isEqualTo(500);
    }
}

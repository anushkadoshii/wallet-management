package com.kosh.ledger.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.kosh.ledger.config.LedgerProperties;
import com.kosh.ledger.config.OverdraftStrategy;
import com.kosh.ledger.domain.Account;
import com.kosh.ledger.domain.AccountStatus;
import com.kosh.ledger.domain.EntryType;
import com.kosh.ledger.repository.AccountRepository;
import com.kosh.ledger.repository.BalanceRepository;
import com.kosh.ledger.repository.JournalEntryRepository;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {
    
    private static final UUID ASHA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RAVI = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock AccountRepository accounts;
    @Mock BalanceRepository balances;
    @Mock JournalEntryRepository entries;

    LedgerService service;

    @BeforeEach
    void setUp() {
        LedgerProperties properties = new LedgerProperties(OverdraftStrategy.CONDITIONAL);
        TransactionRunner transactions = new TransactionRunner(mock(PlatformTransactionManager.class), properties);
        service = new LedgerService(accounts, balances, entries, properties, transactions);
    }

    // Helper Methods

    private static EntryRequest.Line line(UUID accountId, long amount) {
        return new EntryRequest.Line(accountId, amount);
    }

    private static EntryRequest request(EntryRequest.Line... lines) {
        return new EntryRequest("req-1", EntryType.TRANSFER, null, null, null, "test", null, List.of(lines));
    }

    // A fake Account. lenient() = "don't complain if a test never asks for this".
    private static Account account(UUID id, AccountStatus status) {
        Account a = mock(Account.class);
        lenient().when(a.getId()).thenReturn(id);
        lenient().when(a.getStatus()).thenReturn(status);
        return a;
    }

    private void givenAccounts(Account... found) {
        when(accounts.findAllById(any())).thenReturn(List.of(found));
    }

    // validate() Tests

    @Test
    void rejectsFewerThanTwoLines() {
        EntryRequest req = request(line(ASHA, -500));

        assertThatThrownBy(() -> service.post(req))
                .isInstanceOf(InvalidEntryException.class)
                .hasMessageContaining("at least 2");
        verifyNoInteractions(accounts, balances, entries);
    }

    @Test 
    void rejectsZeroAmount() {
        EntryRequest req = request(line(ASHA, 0), line(RAVI, 0));
        assertThatThrownBy(() -> service.post(req))
                .isInstanceOf(InvalidEntryException.class)
                .hasMessageContaining("zero");
        verifyNoInteractions(accounts, balances, entries);
    }

    @Test 
    void rejectsLinesThatDontSumToZero() {
        EntryRequest req = request(line(ASHA, -500), line(RAVI, 400));
        assertThatThrownBy(() -> service.post(req))
                .isInstanceOf(InvalidEntryException.class)
                .hasMessageContaining("-100");
        verifyNoInteractions(accounts, balances, entries);
    }

    @Test
    void rejectsBlankRequestId() {
        EntryRequest req = new EntryRequest(" ", EntryType.TRANSFER, null, null, null, "test", null,
                List.of(line(ASHA, -500), line(RAVI, 500)));

        assertThatThrownBy(() -> service.post(req))
                .isInstanceOf(InvalidEntryException.class)
                .hasMessageContaining("requestId");
        verifyNoInteractions(accounts, balances, entries);
    }

    @Test
    void rejectsMissingActor() {
        EntryRequest req = new EntryRequest("req-1", EntryType.TRANSFER, null, null, null, null, null,
                List.of(line(ASHA, -500), line(RAVI, 500)));

        assertThatThrownBy(() -> service.post(req))
                .isInstanceOf(InvalidEntryException.class)
                .hasMessageContaining("actor");
        verifyNoInteractions(accounts, balances, entries);
    }

    // checkAccount() tests

    @Test
    void rejectsUnknownAccount() {
        givenAccounts(account(ASHA, AccountStatus.ACTIVE));   // RAVI is missing

        assertThatThrownBy(() -> service.post(request(line(ASHA, -500), line(RAVI, 500))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(RAVI.toString());
        verifyNoInteractions(balances, entries);
    }

    @Test
    void frozenAccountCannotSend() {
        givenAccounts(account(ASHA, AccountStatus.FROZEN), account(RAVI, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.post(request(line(ASHA, -500), line(RAVI, 500))))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("FROZEN");
        verifyNoInteractions(balances, entries);
    }

    @Test
    void frozenAccountCanReceive() {
        givenAccounts(account(ASHA, AccountStatus.ACTIVE), account(RAVI, AccountStatus.FROZEN));
        when(balances.debit(ASHA, 500)).thenReturn(1);
        when(balances.credit(RAVI, 500)).thenReturn(1);

        assertThatCode(() -> service.post(request(line(ASHA, -500), line(RAVI, 500))))
                .doesNotThrowAnyException();
    }

    @Test
    void closedAccountCannotReceive() {
        givenAccounts(account(ASHA, AccountStatus.ACTIVE), account(RAVI, AccountStatus.CLOSED));

        assertThatThrownBy(() -> service.post(request(line(ASHA, -500), line(RAVI, 500))))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("CLOSED");
        verifyNoInteractions(balances, entries);
    }

    // balance updates tests

    @Test
    void combinesLinesForTheSameAccount() {
        givenAccounts(account(ASHA, AccountStatus.ACTIVE), account(RAVI, AccountStatus.ACTIVE));
        when(balances.debit(ASHA, 500)).thenReturn(1);
        when(balances.credit(RAVI, 500)).thenReturn(1);

        service.post(request(line(ASHA, -300), line(ASHA, -200), line(RAVI, 500)));

        verify(balances, times(1)).debit(ASHA, 500);   // one combined debit, not two
        verify(balances, times(1)).credit(RAVI, 500);
        verify(entries).save(any());
    }

    @Test
    void zeroDebitRowsMeansInsufficientFunds() {
        givenAccounts(account(ASHA, AccountStatus.ACTIVE), account(RAVI, AccountStatus.ACTIVE));
        when(balances.debit(ASHA, 500)).thenReturn(0);   // "0 rows updated" = not enough money

        assertThatThrownBy(() -> service.post(request(line(ASHA, -500), line(RAVI, 500))))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining(ASHA.toString());
        verify(balances, never()).credit(any(), anyLong());   // stopped before the credit
    }


} 


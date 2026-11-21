-- Run from kosh/services/ledger-service:
--   docker cp ../../tests/sql/break-ledger.sql kosh-postgres:/tmp/break.sql
--   docker exec kosh-postgres psql -U user -d ledger_db -f /tmp/break.sql

\set VERBOSITY terse

\echo '--- 1. Edit history. Expect: ledger is append-only: UPDATE on posting'
BEGIN;
UPDATE posting SET amount = 1
WHERE id = (SELECT id FROM posting LIMIT 1);
ROLLBACK;

\echo '--- 2. Delete history. Expect: ledger is append-only: DELETE on journal_entry'
BEGIN;
DELETE FROM journal_entry
WHERE id = (SELECT id FROM journal_entry LIMIT 1);
ROLLBACK;

\echo '--- 3. Wipe history. Expect: ledger is append-only: TRUNCATE on posting'
BEGIN;
TRUNCATE posting;
ROLLBACK;

\echo '--- 4. Unbalanced entry (+500 / -400). Expect: INSERTs succeed, then "unbalanced: 2 postings, sum 100"'
BEGIN;
INSERT INTO journal_entry (id, request_id, type, actor)
VALUES ('11111111-1111-1111-1111-111111111111', 'break:unbalanced', 'ADJUSTMENT', 'psql');
INSERT INTO posting (entry_id, account_id, amount) VALUES
    ('11111111-1111-1111-1111-111111111111', (SELECT id FROM account WHERE owner_id = 'alice'),   500),
    ('11111111-1111-1111-1111-111111111111', (SELECT id FROM account WHERE type = 'ADJUSTMENT'), -400);
SET CONSTRAINTS ALL IMMEDIATE;   -- run the commit-time check now
ROLLBACK;

\echo '--- 5. Entry with no postings. Expect: "unbalanced: 0 postings, sum 0"'
BEGIN;
INSERT INTO journal_entry (id, request_id, type, actor)
VALUES ('22222222-2222-2222-2222-222222222222', 'break:empty', 'ADJUSTMENT', 'psql');
SET CONSTRAINTS ALL IMMEDIATE;
ROLLBACK;

\echo '--- 6. Balanced entry (+500 / -500), postings inserted one at a time. Expect: NO error'
BEGIN;
INSERT INTO journal_entry (id, request_id, type, actor)
VALUES ('33333333-3333-3333-3333-333333333333', 'break:balanced', 'ADJUSTMENT', 'psql');
INSERT INTO posting (entry_id, account_id, amount)
VALUES ('33333333-3333-3333-3333-333333333333', (SELECT id FROM account WHERE owner_id = 'alice'), 500);
INSERT INTO posting (entry_id, account_id, amount)
VALUES ('33333333-3333-3333-3333-333333333333', (SELECT id FROM account WHERE type = 'ADJUSTMENT'), -500);
SET CONSTRAINTS ALL IMMEDIATE;
\echo 'balanced entry passed the check'
-- ROLLBACK matters here: this entry never updated any balance, so keeping it
-- would make alice's balance disagree with her postings.
ROLLBACK;

\echo '--- Proof nothing was kept. Expect: 0'
SELECT count(*) AS break_entries_left FROM journal_entry WHERE request_id LIKE 'break:%';
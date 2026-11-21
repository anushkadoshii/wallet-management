CREATE OR REPLACE FUNCTION reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append only: % on % is not allowed', TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER journal_entry_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();

CREATE TRIGGER posting_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON posting
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();

CREATE OR REPLACE FUNCTION check_entry_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    eid UUID;
    n INT;
    total BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'posting' THEN eid := NEW.entry_id;
    ELSE eid := NEW.id;
    END IF;

    SELECT COUNT(*), COALESCE(SUM(amount), 0) INTO n, total
    FROM posting
    WHERE entry_id  = eid;

    IF n<2 OR total<>0 THEN
        RAISE EXCEPTION 'entry % is unbalanced: % postings, sum %', eid, n, total;
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER posting_entry_balanced
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED 
    FOR EACH ROW EXECUTE FUNCTION check_entry_balanced();

CREATE CONSTRAINT TRIGGER journal_entry_balanced
    AFTER INSERT ON journal_entry
    DEFERRABLE INITIALLY DEFERRED 
    FOR EACH ROW EXECUTE FUNCTION check_entry_balanced();

INSERT INTO account (owner_id, type) VALUES 
    (NULL, 'BANK_CLEARING'),
    (NULL, 'PAYOUTS_IN_FLIGHT'),
    (NULL, 'FEE_REVENUE'),
    (NULL, 'SUSPENSE'),
    (NULL, 'ADJUSTMENT');

INSERT INTO balance (account_id, overdraft_allowed) 
    SELECT id, TRUE FROM account WHERE type IN ('BANK_CLEARING', 'PAYOUTS_IN_FLIGHT', 'FEE_REVENUE', 'SUSPENSE', 'ADJUSTMENT');
-- 120 рублёвых облигаций сверх основной фикстуры: выдача больше batchLimit по умолчанию (100)
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
SELECT 1000 + n, 'TEST' || lpad(n::text, 4, '0'), '2b0a2f3e-0000-4000-8000-' || lpad(n::text, 12, '0'), 'TEST' || lpad(n::text, 8, '0'),
       'Тестовая облигация ' || n, 'consumer', 'rub', '2030-01-01 00:00:00.000000', 'RISK_LEVEL_MODERATE',
       '2026-09-24 15:30:02.649675', 'SYSTEM', null, null
FROM generate_series(1, 120) AS n;

INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
SELECT 1000 + n, 1000 + n, 'TEST' || lpad(n::text, 4, '0'), '2b0a2f3e-0000-4000-8000-' || lpad(n::text, 12, '0'), 1000.00, 'rub',
       990.00, 'rub', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null
FROM generate_series(1, 120) AS n;

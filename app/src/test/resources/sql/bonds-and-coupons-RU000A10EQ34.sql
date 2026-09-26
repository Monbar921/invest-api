truncate table bond restart identity cascade;
truncate table coupon restart identity cascade;
truncate table price restart identity cascade;

INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 'RU000A10EQ34', 'Авто Финанс Банк БО-001Р-18', 'financial', 'rub',
        '2029-03-14 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (11, 11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (11, 11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

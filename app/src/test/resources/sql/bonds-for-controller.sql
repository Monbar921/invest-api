truncate table bond restart identity cascade;
truncate table coupon restart identity cascade;
truncate table price restart identity cascade;

-- ОФЗ: сектор government, название начинается с "ОФЗ"
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (1, 'SU26238RMFS4', '1c0a2f3e-0001-4000-8000-000000000001', 'RU000A1038V6', 'ОФЗ 26238', 'government', 'rub',
        '2041-05-15 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
-- рублёвые корпоративные облигации с разным уровнем риска
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (2, 'RU000A1080Y2', '1c0a2f3e-0002-4000-8000-000000000002', 'RU000A1080Y2', 'Пушкинское ПЗ 001Р-03', 'consumer', 'rub',
        '2029-03-08 00:00:00.000000', 'RISK_LEVEL_MODERATE', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (3, 'RU000A10EHC1', '1c0a2f3e-0003-4000-8000-000000000003', 'RU000A10EHC1', 'Гидромашсервис 002Р-01', 'consumer', 'rub',
        '2036-01-18 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (4, 'RU000A10EQ34', '1c0a2f3e-0004-4000-8000-000000000004', 'RU000A10EQ34', 'Авто Финанс Банк БО-001Р-18', 'financial', 'rub',
        '2029-03-14 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
-- облигации в иностранной валюте: и номинал, и текущая цена не в рублях
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (5, 'RU000A105A95', '1c0a2f3e-0005-4000-8000-000000000005', 'RU000A105A95', 'Газпром капитал ЗО28-1-Д', 'energy', 'usd',
        '2028-01-26 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (6, 'RU000A106Z77', '1c0a2f3e-0006-4000-8000-000000000006', 'RU000A106Z77', 'Металлоинвест ЗО26-1-Ю', 'materials', 'cny',
        '2026-12-23 00:00:00.000000', 'RISK_LEVEL_MODERATE', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
-- рублёвая облигация, текущая цена по которой ещё не синхронизирована
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by, updated_at, updated_by)
VALUES (7, 'RU000A10ECY6', '1c0a2f3e-0007-4000-8000-000000000007', 'RU000A10ECY6', 'Ойл Ресурс Групп 001Р-02', 'energy', 'rub',
        '2030-10-10 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (1, 1, 'SU26238RMFS4', '1c0a2f3e-0001-4000-8000-000000000001', 1000.00, 'rub', 610.00, 'rub', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (2, 2, 'RU000A1080Y2', '1c0a2f3e-0002-4000-8000-000000000002', 950.00, 'rub', 940.00, 'rub', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (3, 3, 'RU000A10EHC1', '1c0a2f3e-0003-4000-8000-000000000003', 1000.00, 'rub', 950.50, 'rub', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (4, 4, 'RU000A10EQ34', '1c0a2f3e-0004-4000-8000-000000000004', 1000.00, 'rub', 1005.00, 'rub', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (5, 5, 'RU000A105A95', '1c0a2f3e-0005-4000-8000-000000000005', 1000.00, 'usd', 980.00, 'usd', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (6, 6, 'RU000A106Z77', '1c0a2f3e-0006-4000-8000-000000000006', 1000.00, 'cny', 1001.00, 'cny', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (7, 7, 'RU000A10ECY6', '1c0a2f3e-0007-4000-8000-000000000007', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

-- доходность уже посчитана синхронизацией цен; у RU000A1080Y2, RU000A106Z77 и RU000A10ECY6 купонов нет
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (1, 1, 'SU26238RMFS4', '1c0a2f3e-0001-4000-8000-000000000001', 2, true, 7.05, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (3, 3, 'RU000A10EHC1', '1c0a2f3e-0003-4000-8000-000000000003', 4, false, 25.10, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (4, 4, 'RU000A10EQ34', '1c0a2f3e-0004-4000-8000-000000000004', 12, true, 18.50, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (5, 5, 'RU000A105A95', '1c0a2f3e-0005-4000-8000-000000000005', 2, true, 6.20, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

-- id выше заданы явно и последовательности не сдвигают - подтягиваем их, чтобы следующие вставки не упёрлись в bond_pkey
SELECT setval(pg_get_serial_sequence('bond', 'id'), (SELECT max(id) FROM bond));
SELECT setval(pg_get_serial_sequence('coupon', 'id'), (SELECT max(id) FROM coupon));
SELECT setval(pg_get_serial_sequence('price', 'id'), (SELECT max(id) FROM price));

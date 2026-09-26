truncate table bond restart identity cascade;
truncate table coupon restart identity cascade;
truncate table price restart identity cascade;

INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (1, 'RU000A10EHC1', 'ec7ddfb7-b537-4d3d-b7ea-1a3a5960d13e', 'RU000A10EHC1', 'Гидромашсервис 002Р-01', 'consumer', 'rub',
        '2036-01-18 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (2, 'RU000A1080Y2', 'da80302b-be24-4237-9e01-e166eb4b3398', 'RU000A1080Y2', 'Пушкинское ПЗ 001Р-03', 'consumer', 'rub',
        '2029-03-08 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (3, 'RU000A10FC62', '8ce73363-a7ca-4a48-aef5-166fa6a5a6ea', 'RU000A10FC62', 'ГМК Норникель БО-001Р-17', 'materials', 'rub',
        '2030-05-26 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (4, 'RU000A10BGH8', '98aedb87-c957-48c5-bfb1-9cabfc614331', 'RU000A10BGH8', 'Акрон БО-001Р-08', 'materials', 'rub',
        '2027-11-10 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (5, 'RU000A10ADD6', '52245624-6f8f-4490-8cb7-1ce4b1be00b8', 'RU000A10ADD6', 'НСКАТД БО-02', 'other', 'rub',
        '2027-12-03 00:00:00.000000', 'RISK_LEVEL_MODERATE', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (6, 'RU000A10BTA6', 'a84b7e30-3ea0-4f09-89cc-5fc3b45254ca', 'RU000A10BTA6', 'РЖД БО 001P-43R', 'industrials', 'rub',
        '2035-04-27 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (7, 'RU000A10DMN0', 'dc7600f2-db30-4c79-8386-98cc253aee9d', 'RU000A10DMN0', 'реСтор 001Р-02', 'consumer', 'rub',
        '2027-11-21 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (8, 'RU000A108FC2', '00ae13c0-9a07-4992-a54a-4f4d67387727', 'RU000A108FC2', 'ДОМ.РФ 002P-04', 'financial', 'rub',
        '2028-05-19 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (9, 'RU000A10AUG3', '1ac3481f-f318-45e9-8498-9a8132240047', 'RU000A10AUG3', 'АПРИ БО-002Р-07', 'real_estate', 'rub',
        '2030-01-17 00:00:00.000000', 'RISK_LEVEL_MODERATE', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (10, 'RU000A1098F3', 'c0d32be3-9ab0-4ebf-8e02-703113050890', 'RU000A1098F3', 'АФК Система БО 001Р-31', 'financial', 'rub',
        '2028-11-08 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 'RU000A10EQ34', 'Авто Финанс Банк БО-001Р-18', 'financial', 'rub',
        '2029-03-14 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (12, 'RU000A10C0X3', '55a011d6-1f03-477e-b177-02435a84c2b5', 'RU000A10C0X3', 'Эн+ Гидро 001РС-07', 'energy', 'rub',
        '2027-06-29 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (13, 'RU000A10DA66', '05ceceb0-b278-4255-b1ff-79e0ce4f1e58', 'RU000A10DA66', 'Газпром нефть 005P-02R', 'energy', 'rub',
        '2029-04-10 00:00:00.000000', 'RISK_LEVEL_MODERATE', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (14, 'RU000A10EHB3', '642d2fb0-a57d-4cf0-8dbf-5218dfa201e7', 'RU000A10EHB3', 'МФК Лайм-Займ 06', 'financial', 'rub',
        '2031-02-13 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (15, 'RU000A1089K2', '1f92a9ba-cb73-443e-b04e-e219ca0a3788', 'RU000A1089K2', 'РУСАЛ БО-001P-07', 'materials', 'cny',
        '2026-10-09 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (16, 'RU000A10C5B8', '53948dfd-f477-4d4e-95e8-1121acdeb772', 'RU000A10C5B8', 'Денум Солюшнз 001Р-01', 'other', 'rub',
        '2028-07-06 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (17, 'RU000A10DZH4', '4829dd4f-6d9a-45f2-80ab-bb1f1ee1ddca', 'RU000A10DZH4', 'АПРИ БО-002Р-12', 'real_estate', 'rub',
        '2029-06-08 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (18, 'RU000A101S16', '414375ad-f17f-417b-8566-2fe029895ada', 'RU000A101S16', 'Республика Казахстан 13', 'government', 'rub',
        '2031-06-16 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (19, 'RU000A10ECY6', '3b3fda48-20b1-4679-be84-35bfa1117efa', 'RU000A10ECY6', 'Электрорешения 001Р-03', 'other', 'rub',
        '2027-08-19 00:00:00.000000', 'RISK_LEVEL_HIGH', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO bond (id, ticker, uid, isin, name, sector, currency, maturity_date, risk_level, created_at, created_by,
                  updated_at, updated_by)
VALUES (20, 'SU52003RMFS9', '788ced35-f059-4bb1-b9b0-262aee0a2d8b', 'RU000A102069', 'ОФЗ 52003', 'government', 'rub',
        '2030-07-17 00:00:00.000000', 'RISK_LEVEL_LOW', '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (1, 1, 'RU000A10EHC1', 'ec7ddfb7-b537-4d3d-b7ea-1a3a5960d13e', 12, false, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (2, 2, 'RU000A1080Y2', 'da80302b-be24-4237-9e01-e166eb4b3398', 4, false, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (3, 3, 'RU000A10FC62', '8ce73363-a7ca-4a48-aef5-166fa6a5a6ea', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (4, 4, 'RU000A10BGH8', '98aedb87-c957-48c5-bfb1-9cabfc614331', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (5, 5, 'RU000A10ADD6', '52245624-6f8f-4490-8cb7-1ce4b1be00b8', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (6, 6, 'RU000A10BTA6', 'a84b7e30-3ea0-4f09-89cc-5fc3b45254ca', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (7, 7, 'RU000A10DMN0', 'dc7600f2-db30-4c79-8386-98cc253aee9d', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (8, 8, 'RU000A108FC2', '00ae13c0-9a07-4992-a54a-4f4d67387727', 4, false, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (9, 9, 'RU000A10AUG3', '1ac3481f-f318-45e9-8498-9a8132240047', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (10, 10, 'RU000A1098F3', 'c0d32be3-9ab0-4ebf-8e02-703113050890', 4, false, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (11, 11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (12, 12, 'RU000A10C0X3', '55a011d6-1f03-477e-b177-02435a84c2b5', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (13, 13, 'RU000A10DA66', '05ceceb0-b278-4255-b1ff-79e0ce4f1e58', 12, false, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (14, 14, 'RU000A10EHB3', '642d2fb0-a57d-4cf0-8dbf-5218dfa201e7', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (15, 15, 'RU000A1089K2', '1f92a9ba-cb73-443e-b04e-e219ca0a3788', 4, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (16, 16, 'RU000A10C5B8', '53948dfd-f477-4d4e-95e8-1121acdeb772', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (17, 17, 'RU000A10DZH4', '4829dd4f-6d9a-45f2-80ab-bb1f1ee1ddca', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (18, 18, 'RU000A101S16', '414375ad-f17f-417b-8566-2fe029895ada', 2, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (19, 19, 'RU000A10ECY6', '3b3fda48-20b1-4679-be84-35bfa1117efa', 12, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO coupon (id, bond_id, ticker, uid, quantity_per_year, is_fixed_coupon, interest, created_at, created_by, updated_at, updated_by)
VALUES (20, 20, 'SU52003RMFS9', '788ced35-f059-4bb1-b9b0-262aee0a2d8b', 2, true, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (1, 1, 'RU000A10EHC1', 'ec7ddfb7-b537-4d3d-b7ea-1a3a5960d13e', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (2, 2, 'RU000A1080Y2', 'da80302b-be24-4237-9e01-e166eb4b3398', 950.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (3, 3, 'RU000A10FC62', '8ce73363-a7ca-4a48-aef5-166fa6a5a6ea', 1000.00, 'cny', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (4, 4, 'RU000A10BGH8', '98aedb87-c957-48c5-bfb1-9cabfc614331', 1000.00, 'usd', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (5, 5, 'RU000A10ADD6', '52245624-6f8f-4490-8cb7-1ce4b1be00b8', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (6, 6, 'RU000A10BTA6', 'a84b7e30-3ea0-4f09-89cc-5fc3b45254ca', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (7, 7, 'RU000A10DMN0', 'dc7600f2-db30-4c79-8386-98cc253aee9d', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (8, 8, 'RU000A108FC2', '00ae13c0-9a07-4992-a54a-4f4d67387727', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (9, 9, 'RU000A10AUG3', '1ac3481f-f318-45e9-8498-9a8132240047', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (10, 10, 'RU000A1098F3', 'c0d32be3-9ab0-4ebf-8e02-703113050890', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (11, 11, 'RU000A10EQ34', 'ddf7148f-5261-4eb9-994f-d7afc5cf9849', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (12, 12, 'RU000A10C0X3', '55a011d6-1f03-477e-b177-02435a84c2b5', 1000.00, 'cny', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (13, 13, 'RU000A10DA66', '05ceceb0-b278-4255-b1ff-79e0ce4f1e58', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (14, 14, 'RU000A10EHB3', '642d2fb0-a57d-4cf0-8dbf-5218dfa201e7', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (15, 15, 'RU000A1089K2', '1f92a9ba-cb73-443e-b04e-e219ca0a3788', 1000.00, 'cny', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (16, 16, 'RU000A10C5B8', '53948dfd-f477-4d4e-95e8-1121acdeb772', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (17, 17, 'RU000A10DZH4', '4829dd4f-6d9a-45f2-80ab-bb1f1ee1ddca', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (18, 18, 'RU000A101S16', '414375ad-f17f-417b-8566-2fe029895ada', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (19, 19, 'RU000A10ECY6', '3b3fda48-20b1-4679-be84-35bfa1117efa', 1000.00, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);
INSERT INTO price (id, bond_id, ticker, uid, nominal_price, nominal_currency, price, currency, created_at, created_by, updated_at, updated_by)
VALUES (20, 20, 'SU52003RMFS9', '788ced35-f059-4bb1-b9b0-262aee0a2d8b', 1608.87, 'rub', null, null, '2026-09-24 15:30:02.649675', 'SYSTEM', null, null);

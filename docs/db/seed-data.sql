-- SEED DATA — solo per test, NON eseguire in prod
-- ============================================================
-- Dipendenze FK (lookup già presenti in schema-target.sql):
--   regime_fiscale:      1=cedolare_secca
--   stato_prenotazione:  1=imported  2=enriched  3=ready  4=doc_issued  5=settled  6=cancelled
--   stato_documento:     1=draft     2=ready     3=sent_sdi  4=accepted
--   canale_ota:          1=airbnb    2=booking   3=vrbo
--   tipo_immobile:       1=LT
--   tipo_documento:      1=fattura   2=ricevuta
--   sdi_esito:           1=RC (Ricevuta di Consegna)
-- ============================================================


-- ============================================================
-- PREREQUISITO — scenario_fiscale (non popolato in schema-target.sql)
-- ============================================================
INSERT INTO scenario_fiscale (id, codice, descrizione)
VALUES (1, 'scenario_A', 'Sostituto d''imposta PM: fattura PM + ricevuta owner (cedolare secca)');


-- ============================================================
-- TENANT
-- ============================================================
INSERT INTO tenant (id, legal_name, display_name, tax_code, vat_number, stato,
                    administrative_email, pec, phone, legal_address,
                    activated_at, created_at)
VALUES (1,
        'Casa Vacanze Italia SRL', 'Casa Vacanze Italia',
        'CVITRL80A01H501Z', '12345678901', 'active',
        'admin@casavacanze.it', 'casavacanze@pec.it', '+39 06 1234567',
        'Via Roma 1, 00100 Roma RM',
        '2024-01-20', '2024-01-15 00:00:00');


-- ============================================================
-- UTENTI (fk_owner_id sarà impostato con UPDATE dopo owner_profile)
-- ============================================================
INSERT INTO utente (id, fk_tenant_id, email, first_name, last_name,
                    password_hash, ruolo, attivo, created_at)
VALUES
    (1, 1, 'admin@casavacanze.it',      'Laura', 'Bianchi', '{CHANGE_ME}', 'tenant_admin', TRUE, '2024-01-20 00:00:00'),
    (2, 1, 'proprietario@email.it',     'Anna',  'Moretti', '{CHANGE_ME}', 'owner_user',   TRUE, '2024-01-20 00:00:00');


-- ============================================================
-- OWNER PROFILE
-- ============================================================
INSERT INTO owner_profile (id, fk_tenant_id, owner_type, first_name, last_name,
                           tax_code, fk_regime_fiscale_id,
                           email, phone, iban, attivo, created_at)
VALUES (1, 1, 'persona_fisica', 'Anna', 'Moretti',
        'MRTANN85A41H501X', 1,
        'anna.moretti@email.it', '+39 333 1111111',
        'IT60X0542811101000000123456',
        TRUE, '2024-01-20 00:00:00');

-- Collega l'utente owner al proprio profilo proprietario
UPDATE utente SET fk_owner_id = 1 WHERE id = 2;


-- ============================================================
-- PROPERTY (2 immobili di Anna Moretti, PM = Laura Bianchi)
-- ============================================================
INSERT INTO property (id, fk_tenant_id, fk_owner_id, fk_pm_user_id,
                      fk_tipo_immobile_id, internal_code, display_name,
                      address, city, region, cin_code, attivo, created_at)
VALUES
    (1, 1, 1, 1, 1, 'ROM-001', 'Appartamento Trastevere',
     'Via della Scala 15',  'Roma', 'Lazio', 'IT058091C1A2B3C4D5', TRUE, '2024-01-25 00:00:00'),
    (2, 1, 1, 1, 1, 'ROM-002', 'Loft Monti',
     'Via dei Serpenti 80', 'Roma', 'Lazio', 'IT058091C5E6F7G8H9', TRUE, '2024-01-25 00:00:00');


-- ============================================================
-- BOOKING — 1 per stato
-- Calcoli per ogni riga:
--   ota_comm  = gross × aliquota canale (airbnb 15%, booking 18%, vrbo 8%)
--   pm_fee    = gross × 20%
--   owner_net = gross - ota_comm - cleaning - pm_fee
--   withhold  = owner_net × 21%
--   tassa_sog = guests × min(nights, 10 max Roma) × 3.50 €
-- ============================================================
INSERT INTO booking (
    id, fk_tenant_id, fk_property_id, fk_canale_ota_id, fk_scenario_fiscale_id,
    external_booking_id, guest_name, guest_tax_code,
    checkin_date, checkout_date, nights, guests,
    gross_amount, ota_commission_amount, cleaning_amount, pm_fee_amount,
    owner_net_amount, withholding_amount,
    tourist_tax_amount, tourist_tax_included_in_gross, tourist_tax_collection,
    fk_stato_prenotazione_id, payment_status, fk_stato_documento_id, settlement_status,
    created_at
)
VALUES

    -- stato 1: imported — bozza, nulla elaborato
    -- gross=200, ota=30(15%), cleaning=60, pm=40(20%), net=70, wh=14.70
    (1, 1, 1, 1, 1,
     'AIRBNB-20260523-0001', 'John Smith', 'SMTJHN85A01H501X',
     '2026-05-20', '2026-05-23', 3, 2,
     200.00, 30.00, 60.00, 40.00, 70.00, 14.70,
     21.00, TRUE, 'contanti',
     1, 'pending', 1, 'pending',
     '2026-05-20 00:00:00'),

    -- stato 2: enriched — CF ospite e tassa soggiorno confermati
    -- gross=340, ota=61.20(18%), cleaning=70, pm=68(20%), net=140.80, wh=29.57
    (2, 1, 2, 2, 1,
     'BOOKING-20260505-0002', 'Marie Dupont', 'DPNMRA90B42F205Z',
     '2026-05-01', '2026-05-05', 4, 2,
     340.00, 61.20, 70.00, 68.00, 140.80, 29.57,
     28.00, FALSE, 'payment_link',
     2, 'pending', 2, 'pending',
     '2026-05-01 00:00:00'),

    -- stato 3: ready — pronta per emissione, doc in sent_sdi
    -- gross=380, ota=30.40(8%), cleaning=70, pm=76(20%), net=203.60, wh=42.76
    (3, 1, 1, 3, 1,
     'VRBO-20260414-0003', 'Hans Müller', 'MLLHNS80C03D612Y',
     '2026-04-10', '2026-04-14', 4, 3,
     380.00, 30.40, 70.00, 76.00, 203.60, 42.76,
     42.00, FALSE, 'payment_link',
     3, 'pending', 3, 'calculated',
     '2026-04-10 00:00:00'),

    -- stato 4: doc_issued — documento accettato da SDI, in attesa liquidazione
    -- gross=320, ota=48(15%), cleaning=60, pm=64(20%), net=148, wh=31.08
    (4, 1, 2, 1, 1,
     'AIRBNB-20260319-0004', 'Yuki Tanaka', 'TNKYKU75D44L219V',
     '2026-03-15', '2026-03-19', 4, 2,
     320.00, 48.00, 60.00, 64.00, 148.00, 31.08,
     28.00, TRUE, 'contanti',
     4, 'received', 4, 'approved',
     '2026-03-15 00:00:00'),

    -- stato 5: settled — liquidata al proprietario
    -- gross=180, ota=32.40(18%), cleaning=50, pm=36(20%), net=61.60, wh=12.94
    (5, 1, 1, 2, 1,
     'BOOKING-20260213-0005', 'Carlos García', 'GRCCRL88E05F839Q',
     '2026-02-10', '2026-02-13', 3, 1,
     180.00, 32.40, 50.00, 36.00, 61.60, 12.94,
     10.50, FALSE, 'payment_link',
     5, 'received', 4, 'paid',
     '2026-02-10 00:00:00'),

    -- stato 6: cancelled — annullata, nessun documento
    -- gross=210, ota=31.50(15%), cleaning=60, pm=42(20%), net=76.50, wh=16.07
    (6, 1, 2, 1, 1,
     'AIRBNB-20260513-0006', 'Emma Wilson', 'WLSEMM92F46H501R',
     '2026-05-10', '2026-05-13', 3, 2,
     210.00, 31.50, 60.00, 42.00, 76.50, 16.07,
     21.00, TRUE, 'contanti',
     6, 'pending', 1, 'pending',
     '2026-05-10 00:00:00');


-- ============================================================
-- FISCAL DOCUMENT
-- Doc 1: fattura per booking doc_issued (b4, Yuki Tanaka)
--   imponibile = ota_comm(48) + cleaning(60) + pm_fee(64) = 172.00
--   IVA 22%    = 37.84  →  totale = 209.84
-- Doc 2: ricevuta per booking settled (b5, Carlos García)
--   totale = gross_amount = 180.00, fuori campo IVA
-- ============================================================
INSERT INTO fiscal_document (
    id, fk_tenant_id, fk_booking_id, fk_tipo_documento_id,
    fk_sdi_esito_id, document_number, issue_date,
    recipient_name, recipient_tax_code,
    total_amount, vat_amount,
    fk_stato_documento_id, sdi_identifier,
    created_at
)
VALUES
    (1, 1, 4, 1,
     1, 'FT-2025-0001', '2026-03-19',
     'Yuki Tanaka', 'TNKYKU75D44L219V',
     209.84, 37.84,
     4, 'SDI00000001',
     '2026-03-19 00:00:00'),

    (2, 1, 5, 2,
     NULL, 'RIC-2025-0001', '2026-02-13',
     'Carlos García', 'GRCCRL88E05F839Q',
     180.00, 0.00,
     4, NULL,
     '2026-02-13 00:00:00');


-- ============================================================
-- RESET SEQUENZE
-- Necessario dopo INSERT con ID espliciti su colonne SERIAL.
-- ============================================================
SELECT setval('scenario_fiscale_id_seq', 1);
SELECT setval('tenant_id_seq',           1);
SELECT setval('utente_id_seq',           2);
SELECT setval('owner_profile_id_seq',    1);
SELECT setval('property_id_seq',         2);
SELECT setval('booking_id_seq',          6);
SELECT setval('fiscal_document_id_seq',  2);

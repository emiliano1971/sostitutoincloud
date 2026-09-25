-- ============================================
-- CLEANUP COMPLETO BOOKING :booking_id
-- Sostituisci :booking_id con l'ID reale
-- prima di eseguire.
-- ATTENZIONE: operazione irreversibile.
-- ============================================
--
-- Esecuzione con variabile psql (consigliata):
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v booking_id=91 -f docs/db/cleanup-booking.sql
--
-- In alternativa: sostituisci a mano ogni occorrenza di :booking_id.
--
-- Ordine delle dipendenze FK (tutte verificate su schema-target.sql e su pg_constraint):
--     settlement_booking.fk_booking_id      -> booking            ON DELETE RESTRICT
--     withholding_ledger.fk_booking_id      -> booking            ON DELETE RESTRICT
--     withholding_ledger.fk_fiscal_document_id -> fiscal_document ON DELETE RESTRICT
--     withholding_ledger.fk_f24_record_id   -> f24_record         ON DELETE SET NULL
--     fiscal_document.fk_booking_id         -> booking            ON DELETE RESTRICT
--     fiscal_document.fk_documento_collegato_id -> fiscal_document ON DELETE SET NULL
--     settlement_booking.fk_settlement_id   -> settlement         ON DELETE CASCADE
--     booking_split_economico.fk_booking_id -> booking            ON DELETE CASCADE  (migration 018)
-- Le FK RESTRICT sopra vanno rispettate a mano: è lo stesso ordine di
-- BookingService.deleteWithCascade().
-- booking_split_economico è l'unica ON DELETE CASCADE verso booking: le righe split
-- spariscono da sole con il DELETE del punto 7, senza un passo dedicato.
--
-- NON tocca: f24_record, cu_record, settlement, sdi_progressivo — sono entità
-- aggregate che riguardano più prenotazioni (vedi sezione 9, dati da ricalcolare).

\set ON_ERROR_STOP on

-- 0. Verifica cosa esiste prima di cancellare
--    Le colonne stato di cu_record / f24_record / settlement sono enum PostgreSQL
--    (cu_status, f24_status, settlement_status): nelle UNION vanno castate a ::text,
--    altrimenti "in UNION i tipi character varying e cu_status non combaciano".
SELECT 'booking' AS tabella, id::text AS id, external_booking_id AS dettaglio
FROM booking WHERE id = :booking_id
UNION ALL
SELECT 'fiscal_document', fd.id::text, fd.document_number
FROM fiscal_document fd WHERE fd.fk_booking_id = :booking_id
UNION ALL
SELECT 'withholding_ledger', wl.id::text, wl.stato
FROM withholding_ledger wl WHERE wl.fk_booking_id = :booking_id
UNION ALL
SELECT 'settlement_booking', sb.id::text, sb.fk_settlement_id::text
FROM settlement_booking sb WHERE sb.fk_booking_id = :booking_id
UNION ALL
SELECT 'cu_record (indiretto)', cr.id::text, cr.stato::text
FROM cu_record cr
JOIN withholding_ledger wl ON wl.fk_owner_id = cr.fk_owner_id
  AND wl.fk_tenant_id = cr.fk_tenant_id
WHERE wl.fk_booking_id = :booking_id;

-- 0b. STOP se una di queste è già chiusa: un F24 pagato o una liquidazione pagata
--     non vanno più toccati. Se qui esce almeno una riga, valutare prima di procedere.
SELECT 'f24_record (indiretto)' AS tabella, f.id::text AS id, f.stato::text AS dettaglio
FROM f24_record f
JOIN withholding_ledger wl ON wl.fk_f24_record_id = f.id
WHERE wl.fk_booking_id = :booking_id
  AND f.stato IN ('sent', 'paid')
UNION ALL
SELECT 'settlement (indiretto)', s.id::text, s.stato::text
FROM settlement s
JOIN settlement_booking sb ON sb.fk_settlement_id = s.id
WHERE sb.fk_booking_id = :booking_id
  AND s.stato IN ('approved', 'paid');

BEGIN;

-- 1. Scollega dal F24 (NON elimina l'F24)
UPDATE withholding_ledger
SET fk_f24_record_id = NULL,
    stato = 'da_versare',
    updated_at = NOW()
WHERE fk_booking_id = :booking_id
  AND fk_f24_record_id IS NOT NULL;

-- 2. Elimina settlement_booking
DELETE FROM settlement_booking
WHERE fk_booking_id = :booking_id;

-- 3a. Audit delle ritenute: va cancellato PRIMA delle righe, altrimenti gli id
--     a cui punta entity_id spariscono e le tracce restano orfane
--     (audit_log non ha FK: nessun errore, solo record scollegati).
DELETE FROM audit_log
WHERE entity_type = 'WithholdingLedger'
  AND entity_id IN (SELECT id FROM withholding_ledger WHERE fk_booking_id = :booking_id);

-- 3b. Elimina withholding_ledger
--     (prima di fiscal_document: fk_fiscal_document_id è RESTRICT e NOT NULL)
DELETE FROM withholding_ledger
WHERE fk_booking_id = :booking_id;

-- 4. sdi_file: la tabella NON esiste in questo schema.
--    Il file XML inviato allo SDI sta sul filesystem e il percorso è su
--    fiscal_document.sdi_file_path: se serve va cancellato a mano DOPO aver
--    letto i percorsi (la query sotto va eseguita PRIMA della sezione 5b).
--    sdi_progressivo è un contatore globale per applicazione, senza FK verso il
--    documento: il progressivo già consumato NON viene recuperato.
SELECT fd.id, fd.document_number, fd.sdi_progressivo, fd.sdi_file_path
FROM fiscal_document fd
WHERE fd.fk_booking_id = :booking_id
  AND fd.sdi_file_path IS NOT NULL;

-- 5a. Audit dei documenti fiscali: stessa ragione del punto 3a.
DELETE FROM audit_log
WHERE entity_type = 'FiscalDocument'
  AND entity_id IN (SELECT id FROM fiscal_document WHERE fk_booking_id = :booking_id);

-- 5b. Elimina fiscal_document
--     Ricevuta e fattura dello stesso booking si referenziano a vicenda con
--     fk_documento_collegato_id: la FK è ON DELETE SET NULL, quindi la DELETE
--     unica che le elimina entrambe non viola nulla.
DELETE FROM fiscal_document
WHERE fk_booking_id = :booking_id;

-- 6. Elimina audit_log del booking
DELETE FROM audit_log
WHERE entity_type = 'Booking'
  AND entity_id = :booking_id;

-- 7. Elimina il booking
DELETE FROM booking
WHERE id = :booking_id;

-- 8. Verifica finale (dentro la transazione, prima del COMMIT)
SELECT COUNT(*) AS residui_fiscal_document
FROM fiscal_document WHERE fk_booking_id = :booking_id;
SELECT COUNT(*) AS residui_withholding
FROM withholding_ledger WHERE fk_booking_id = :booking_id;
SELECT COUNT(*) AS residui_settlement_booking
FROM settlement_booking WHERE fk_booking_id = :booking_id;
SELECT COUNT(*) AS residui_booking
FROM booking WHERE id = :booking_id;

-- Per una prova a vuoto sostituisci COMMIT con ROLLBACK.
COMMIT;

-- ============================================
-- 9. DOPO IL CLEANUP — totali da ricalcolare
-- ============================================
-- Le entità aggregate restano con i totali del vecchio booking:
--   f24_record.total_amount   -> ricalcolo dall'applicazione (azione f24.ricalcola)
--   settlement.total_amount / withholding_amount / net_amount
--   cu_record.total_compensi / total_imponibile / total_ritenute
-- Non li ricalcolo qui: vanno rifatti dai servizi, che applicano le regole fiscali.
-- Controllo degli scostamenti rimasti (eseguire a cleanup avvenuto):
--
-- SELECT f.id, f.stato, f.total_amount AS totale_memorizzato,
--        COALESCE(SUM(wl.ritenuta_amount), 0) AS totale_ricalcolato
-- FROM f24_record f
-- LEFT JOIN withholding_ledger wl ON wl.fk_f24_record_id = f.id
-- GROUP BY f.id, f.stato, f.total_amount
-- HAVING f.total_amount <> COALESCE(SUM(wl.ritenuta_amount), 0);

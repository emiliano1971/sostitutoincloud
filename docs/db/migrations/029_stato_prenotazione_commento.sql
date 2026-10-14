-- ============================================
-- Migration 029: commento della lookup stato_prenotazione allineato al flusso reale
-- ============================================
--
-- Il commento della migration iniziale non citava 'stornata' (migration 025) e lasciava
-- intendere che solo gli stati con finale = TRUE fermino il workflow. Solo commento: nessun
-- dato cambia ('stornata' resta finale = FALSE perché è reversibile annullando la NDC).
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/029_stato_prenotazione_commento.sql

COMMENT ON TABLE stato_prenotazione IS
    'Stati del workflow di una prenotazione: imported → enriched → ready → doc_issued → settled; '
    'cancelled per le prenotazioni annullate. '
    'doc_issued/ready → stornata con la nota di credito totale sulla fattura PM: blocca '
    'l''emissione di nuovi documenti e il ricalcolo dello stato; si esce annullando la NDC '
    '(torna doc_issued o ready). Per riemettere i documenti la si copia in una nuova '
    'prenotazione (fk_booking_origine_id): l''originale resta stornata. '
    'finale = TRUE solo per gli stati terminali non reversibili (settled, cancelled). '
    'Lookup per consentire nuovi stati senza modificare il codice.';

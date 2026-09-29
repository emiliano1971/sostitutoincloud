-- ============================================
-- Migration 023: booking_split_economico — provvigione PM in fondo alle voci
-- Solo dati, nessuna modifica di schema.
-- ============================================
--
-- La riga commissione_pm passa da ordinamento 30 a 1000: nello split viene dopo le voci
-- extra (da 50 in su) e subito prima di "Aggiungi voce".
-- Ordine delle voci: OTA 10, pulizie 20, cambio biancheria 25, tassa 40 (mostrata a parte),
-- extra da 50, PM 1000. Dal codice: BookingService.ORDINE_PM (popolaSplitEconomico) e
-- aggiungiVoceExtra(), che numera le extra escludendo la PM dal massimo.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/023_split_ordinamento_pm.sql

BEGIN;

-- Anche le righe eliminate logicamente: se tornassero visibili resterebbero coerenti
UPDATE booking_split_economico
   SET ordinamento = 1000
 WHERE tipo_voce = 'commissione_pm'
   AND ordinamento <> 1000;

COMMIT;

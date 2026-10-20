-- Provvigione PM imponibile (voce 'commissione_pm' dello split) per prenotazione.
-- Escluse le prenotazioni stornate e cancellate (come provvigione_pm_per_mese.sql, dashboard).
-- Usata dalla lista prenotazioni (colonna "Netto PM") sui soli booking della pagina.
-- Parametri: fk_tenant_id, array degli id booking
SELECT
    bse.fk_booking_id,
    COALESCE(SUM(bse.imponibile), 0) AS provvigione_pm
FROM booking_split_economico bse
JOIN booking b
    ON b.id = bse.fk_booking_id
JOIN stato_prenotazione sp
    ON sp.id = b.fk_stato_prenotazione_id
WHERE bse.fk_tenant_id = ?
  AND bse.fk_booking_id = ANY(?)
  AND bse.tipo_voce = 'commissione_pm'
  AND bse.deleted_at IS NULL
  AND sp.codice NOT IN ('stornata', 'cancelled')
GROUP BY bse.fk_booking_id

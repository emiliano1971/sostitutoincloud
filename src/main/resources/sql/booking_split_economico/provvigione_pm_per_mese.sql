-- Provvigione PM imponibile (voce 'commissione_pm' dello split) per mese di check-in.
-- Escluse le prenotazioni stornate e cancellate. Usata dalla dashboard (grafico 12 mesi).
-- Parametri: fk_tenant_id, check-in da (incluso), check-in a (escluso)
SELECT
    DATE_TRUNC('month', b.checkin_date)::date AS mese,
    SUM(bse.imponibile)                       AS provvigione_pm
FROM booking_split_economico bse
JOIN booking b
    ON b.id = bse.fk_booking_id
JOIN stato_prenotazione sp
    ON sp.id = b.fk_stato_prenotazione_id
WHERE bse.fk_tenant_id = ?
  AND bse.tipo_voce = 'commissione_pm'
  AND bse.deleted_at IS NULL
  AND sp.codice NOT IN ('stornata', 'cancelled')
  AND b.checkin_date >= ?
  AND b.checkin_date < ?
GROUP BY DATE_TRUNC('month', b.checkin_date)
ORDER BY mese ASC

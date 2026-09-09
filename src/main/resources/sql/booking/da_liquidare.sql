-- Prenotazioni con ricevuta emessa ('doc_issued') non ancora incluse in alcuna
-- liquidazione: sono quelle che il prossimo calcolo dovrebbe raccogliere.
--
-- Note:
--  * lo stato è risolto per codice sulla lookup, non per id;
--  * fiscal_document è agganciato via withholding_ledger.fk_fiscal_document_id
--    (non via fk_booking_id) per non moltiplicare le righe quando un booking ha
--    più documenti: la ritenuta ha un solo documento di origine
--    (vincolo uq_withholding_per_document);
--  * l'owner arriva da withholding_ledger.fk_owner_id, che è NOT NULL,
--    mentre booking.fk_owner_id è nullable;
--  * nessun filtro su withholding_ledger.stato: il versamento della ritenuta
--    (F24) è indipendente dalla liquidazione all'owner.
--
-- Parametri: 1) tenantId
SELECT b.id                                  AS booking_id,
       b.external_booking_id                 AS external_booking_id,
       COALESCE(NULLIF(TRIM(CONCAT(op.first_name, ' ', op.last_name)), ''),
                op.legal_name)               AS owner_name,
       p.display_name                        AS property_name,
       b.checkin_date                        AS checkin_date,
       b.checkout_date                       AS checkout_date,
       fd.canone_locazione                   AS canone_locazione,
       fd.ritenuta_amount                    AS ritenuta_amount,
       wl.periodo_mese                       AS periodo_mese,
       wl.periodo_anno                       AS periodo_anno
  FROM booking b
  JOIN stato_prenotazione sp ON sp.id = b.fk_stato_prenotazione_id
  JOIN withholding_ledger wl ON wl.fk_booking_id = b.id
  JOIN fiscal_document fd    ON fd.id = wl.fk_fiscal_document_id
  JOIN tipo_documento td     ON td.id = fd.fk_tipo_documento_id
                            AND td.codice = 'ricevuta'
  JOIN owner_profile op      ON op.id = wl.fk_owner_id
  JOIN property p            ON p.id = b.fk_property_id
 WHERE b.fk_tenant_id = ?
   AND sp.codice = 'doc_issued'
   AND NOT EXISTS (SELECT 1
                     FROM settlement_booking sb
                    WHERE sb.fk_booking_id = b.id)
 ORDER BY wl.periodo_anno, wl.periodo_mese, op.last_name, b.id

-- Righe di ritenuta di un periodo (mese/anno), con i dati della prenotazione e
-- dell'immobile già risolti. Alimenta GET /api/withholding-ledger.
--
-- Stessa proiezione di righe_f24.sql: cambia solo il filtro (periodo invece di
-- modello F24) e l'ordinamento, qui per data dell'evento come nella query che
-- questa sostituisce. Se si aggiunge una colonna, aggiornare entrambi i file.
--
-- Parametri: 1) tenantId  2) anno  3) mese
SELECT wl.id                                 AS id,
       wl.fk_booking_id                      AS booking_id,
       b.external_booking_id                 AS external_booking_id,
       b.guest_name                          AS guest_name,
       COALESCE(NULLIF(TRIM(CONCAT(op.first_name, ' ', op.last_name)), ''),
                op.legal_name)               AS owner_name,
       p.display_name                        AS property_name,
       b.checkin_date                        AS checkin_date,
       b.checkout_date                       AS checkout_date,
       fd.document_number                    AS document_number,
       wl.data_evento                        AS data_evento,
       wl.periodo_mese                       AS periodo_mese,
       wl.periodo_anno                       AS periodo_anno,
       wl.canone_locazione                   AS canone_locazione,
       wl.aliquota_ritenuta                  AS aliquota_ritenuta,
       wl.ritenuta_amount                    AS ritenuta_amount,
       wl.stato                              AS stato,
       wl.fk_f24_record_id                   AS fk_f24_record_id
FROM withholding_ledger wl
         JOIN booking b ON b.id = wl.fk_booking_id
         JOIN property p ON p.id = b.fk_property_id
         JOIN owner_profile op ON op.id = wl.fk_owner_id
         LEFT JOIN fiscal_document fd ON fd.id = wl.fk_fiscal_document_id
WHERE wl.fk_tenant_id = ?
  AND wl.periodo_anno = ?
  AND wl.periodo_mese = ?
ORDER BY wl.data_evento, wl.id

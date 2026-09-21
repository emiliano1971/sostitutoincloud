-- Righe di ritenuta agganciate a un modello F24, con i dati della prenotazione
-- e dell'immobile necessari al dettaglio a video e alla stampa.
--
-- Note:
--  * sostituisce il caricamento riga-per-riga via BookingService.findById(), che
--    per ogni ritenuta ricalcolava l'intero split economico dal contratto;
--  * il proprietario arriva da withholding_ledger.fk_owner_id (NOT NULL),
--    mentre booking.fk_owner_id è nullable;
--  * fiscal_document in LEFT JOIN: il numero documento è informativo e la
--    ritenuta resta valida anche senza;
--  * periodo_mese/periodo_anno restano separati: il formato MM/YYYY e il
--    confronto con il periodo dell'F24 (arretrati) sono responsabilità del service;
--  * stessa proiezione di righe_periodo.sql, che filtra per periodo invece che per
--    modello F24: se si aggiunge una colonna, aggiornare entrambi i file.
--
-- Parametri: 1) f24RecordId  2) tenantId
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
WHERE wl.fk_f24_record_id = ?
  AND wl.fk_tenant_id = ?
ORDER BY b.checkin_date, wl.id

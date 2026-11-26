-- Ricevuta attiva più recente di ogni prenotazione del settlement (annullate escluse).
-- Una riga per prenotazione: DISTINCT ON tiene la più recente per data di emissione,
-- a parità di data quella con l'id più alto. Prenotazioni senza ricevuta: nessuna riga.
-- Parametri: 1 = settlement id, 2 = tenant id
SELECT DISTINCT ON (fd.fk_booking_id)
       fd.fk_booking_id   AS booking_id,
       fd.id              AS ricevuta_id,
       fd.document_number AS ricevuta_number
  FROM fiscal_document fd
  JOIN settlement_booking sb ON sb.fk_booking_id = fd.fk_booking_id
  JOIN tipo_documento td     ON td.id = fd.fk_tipo_documento_id
  JOIN stato_documento st    ON st.id = fd.fk_stato_documento_id
 WHERE sb.fk_settlement_id = ?
   AND fd.fk_tenant_id = ?
   AND td.codice = 'ricevuta'
   AND st.codice <> 'annullata'
 ORDER BY fd.fk_booking_id, fd.issue_date DESC, fd.id DESC

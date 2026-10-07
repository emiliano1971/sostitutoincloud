package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Filtri e ordinamento della lista documenti fiscali (GET /api/documents). Campi null = nessun filtro. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFilterDTO {

    private String stato;           // codice stato_documento
    private String tipo;            // codice tipo_documento: fattura | ricevuta | nota_credito
    private String search;          // numero, destinatario, proprietario, prenotazione, NDC/fattura collegata
    private String filtroFiscale;   // da_liquidare | f24_non_pagato | senza_cu (solo ricevute attive)
    private LocalDate dataFrom;     // data emissione, estremi inclusi
    private LocalDate dataTo;
    private Integer ownerId;
    private String liquidazione;    // stato settlement (pending/calculated/approved/paid) | none = non liquidato
    private String sort;            // campo di DocumentListDTO; default issueDate
    private String dir;             // asc | desc (default desc)
}

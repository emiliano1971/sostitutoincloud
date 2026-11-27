package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Risposta di GET /api/documents/{id}/cambio-data-controllo?data=: intervallo ammesso per la
 * nuova data (nella serie, anno invariato), blocchi e avvisi. Senza data valuta solo il
 * documento; con la data anche quella. Non scrive nulla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambioDataControlloDTO {
    private boolean consentito;
    private List<String> blocchi;
    private List<Avviso> avvisi;
    private LocalDate dataMinima;
    private LocalDate dataMassima;
    private LocalDate dataAttuale;
    /** Solo ricevuta: true se il cambio di mese non è consentito (intervallo già ristretto al mese). */
    private Boolean limitatoAlMese;
    /** Solo ricevuta con limitatoAlMese: motivo concreto (F24, rendiconto, stato della ritenuta). */
    private String motivoLimiteMese;
    /**
     * Solo ricevuta con data in un altro mese: true se tra gli avvisi c'è una scadenza F24 superata
     * (SCADENZA_F24_SUPERATA, SCADENZA_ORIGINE_SUPERATA). Il cambio richiede confermaScadenza=true.
     */
    private Boolean richiedeConfermaScadenza;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avviso {
        /**
         * DATA_PRECEDE_CHECKOUT, INVIO_SDI_TARDIVO, EMAIL_GIA_INVIATA, NDC_CREDITO_PERIODO, RICEVUTA_PERIODO_SPOSTATO,
         * F24_RICALCOLATO, RENDICONTO_RICALCOLATO, F24_DESTINAZIONE, RENDICONTO_DESTINAZIONE,
         * SCADENZA_F24_SUPERATA, SCADENZA_ORIGINE_SUPERATA
         */
        private String codice;
        private String messaggio;
    }
}

package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Risposta di GET /api/documents/emissione-controllo: intervallo ammesso per la data di
 * emissione e avvisi non bloccanti sul periodo della data scelta. Non scrive nulla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissioneControlloDTO {
    /** Data dell'ultimo documento dello stesso tipo e anno, altrimenti 1 gennaio. */
    private LocalDate dataMinima;
    /** Oggi: date future non ammesse. */
    private LocalDate dataMassima;
    /** Documento che fissa la data minima (null se nessun documento nell'anno). */
    private String ultimoDocumentoNumero;
    private LocalDate ultimoDocumentoData;
    /** Data controllata (quella richiesta, oppure oggi). */
    private LocalDate data;
    /** null se la data è ammessa, altrimenti lo stesso messaggio del 422 di emissione. */
    private String erroreData;
    private List<Avviso> avvisi;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avviso {
        /** F24_PAGATO, F24_NON_PAGATO, CU_EMESSA, ANNO_PRECEDENTE, INVIO_SDI_TARDIVO, DATA_PRECEDE_CHECKOUT */
        private String codice;
        private String messaggio;
        /** Solo per F24_NON_PAGATO: F24 da ricalcolare dopo l'emissione. */
        private Integer f24Id;
    }
}

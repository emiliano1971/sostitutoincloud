package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Risposta di GET /api/documents/{id}/eliminazione-controllo: se l'eliminazione fisica è
 * consentita, i motivi del blocco, gli avvisi da confermare e i documenti che verrebbero
 * eliminati (fattura PM e ricevuta collegata, oppure la sola ricevuta). Non scrive nulla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminazioneControlloDTO {
    private boolean consentito;
    private List<String> blocchi;
    private List<Avviso> avvisi;
    private List<DocumentoCoinvolto> documenti;
    /**
     * Solo per la fattura: eliminazione della sola fattura (blocchi della sola fattura). I campi
     * sopra coincidono con scenarioConRicevuta se presente, altrimenti con questo.
     */
    private Scenario scenarioSoloFattura;
    /** Solo per la fattura con una ricevuta attiva sulla prenotazione: fattura + ricevuta, tutto o niente. */
    private Scenario scenarioConRicevuta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scenario {
        private boolean consentito;
        private List<String> blocchi;
        private List<Avviso> avvisi;
        private List<DocumentoCoinvolto> documenti;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avviso {
        /** F24_AGGIORNATO, LIQUIDAZIONE_DA_RICALCOLARE, CU_DA_RIGENERARE, CU_VERRA_ELIMINATA, EMAIL_GIA_INVIATA, BOLLO_ADDEBITATO, SDI_MANUALE_ANNULLATO, RICEVUTA_RESTA, RICEVUTE_MULTIPLE */
        private String codice;
        private String messaggio;
        /** Solo F24_AGGIORNATO: F24 e suo periodo (link alla lista F24 filtrata). */
        private Integer f24Id;
        private Integer f24Anno;
        private Integer f24Mese;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentoCoinvolto {
        private Integer id;
        private String numero;
        /** Codice lookup tipo_documento: fattura | ricevuta */
        private String tipo;
    }
}

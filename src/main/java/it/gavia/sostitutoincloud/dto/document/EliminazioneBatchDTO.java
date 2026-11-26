package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Risposta dell'eliminazione in blocco: controllo (simulazione=true, non scrive nulla) ed
 * esecuzione (simulazione=false, con il riferimento al lotto). Ogni unità è una fattura con la
 * ricevuta della prenotazione, oppure una ricevuta senza fattura.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminazioneBatchDTO {
    /** true = controllo: "eliminati" sono quelli che verrebbero eliminati. */
    private boolean simulazione;
    /** Solo esecuzione: riferimento del lotto, riportato nell'audit di ogni documento. */
    private String lotto;
    /** Almeno un'unità eliminabile. */
    private boolean consentito;
    private List<Unita> eliminati;
    /** Con i blocchi: la loro serie si ferma qui. */
    private List<Unita> bloccati;
    /** Serie interrotta da un documento bloccato o non tentato: motivo in {@link Unita#motivo}. */
    private List<Unita> nonTentati;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Unita {
        private List<EliminazioneControlloDTO.DocumentoCoinvolto> documenti;
        private List<String> blocchi;
        private List<EliminazioneControlloDTO.Avviso> avvisi;
        /** Solo non tentati: es. "serie interrotta da FT-2026-0012". */
        private String motivo;
        /** La ricevuta della prenotazione viene eliminata con la fattura senza essere stata selezionata. */
        private boolean ricevutaInclusa;
    }
}

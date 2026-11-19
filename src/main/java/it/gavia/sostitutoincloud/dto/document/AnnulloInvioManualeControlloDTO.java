package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Risposta di GET /api/documents/{id}/annulla-invio-manuale-controllo: se l'invio manuale allo
 * SDI si può annullare (documento di nuovo 'ready'), i motivi del blocco e gli avvisi da
 * confermare. Non scrive nulla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnulloInvioManualeControlloDTO {
    private boolean consentito;
    private List<String> blocchi;
    private List<Avviso> avvisi;
    /** Data dell'invio manuale che verrebbe annullato (sdi_sent_at). */
    private LocalDateTime inviatoManualmenteAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avviso {
        /** RIAPERTURA (sempre), NDC_RIAPERTA (nota di credito) */
        private String codice;
        private String messaggio;
    }
}

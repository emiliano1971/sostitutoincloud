package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup di una liquidazione generata da un test.
 * La liquidazione deve appartenere al tenant del chiamante.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupSettlementDTO {

    private Integer settlementId;

    /**
     * Consente di eliminare anche una liquidazione in stato 'paid'. Di norma è false —
     * una liquidazione pagata non si tocca — ma il test della fase 06 paga quella che ha
     * appena calcolato lui e deve poterla rimuovere per restare ripetibile.
     */
    private Boolean forzaSePagato;
}

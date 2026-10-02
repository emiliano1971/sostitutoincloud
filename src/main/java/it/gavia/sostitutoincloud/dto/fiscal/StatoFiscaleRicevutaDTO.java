package it.gavia.sostitutoincloud.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Stato fiscale di una ricevuta owner (GET /api/documents/{id}/stato-fiscale): ritenuta e
 * F24, liquidazione, CU. Gli stati sono i CODICI del DB, tradotti in etichette dal frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatoFiscaleRicevutaDTO {

    private Integer documentId;
    private String documentNumber;
    /** Codice stato del documento: 'annullata' se stornata da NDC. */
    private String statoDocumento;
    private String proprietarioNome;
    private BigDecimal canoneLocazione;
    private BigDecimal ritenutaAmount;
    private BigDecimal aliquotaRitenuta;

    // Ritenuta e F24
    /** withholding_ledger.stato: da_versare / versata / stornata / credito_imposta; null se non registrata. */
    private String ritenutaStato;
    /** f24_record.stato (draft/ready/sent/paid/error); null = ritenuta non ancora in un F24. */
    private String f24Stato;
    /** "MM/YYYY" */
    private String f24Periodo;
    private Integer f24Id;
    private Integer f24Mese;
    private Integer f24Anno;

    // Liquidazione
    /** settlement.stato (pending/calculated/approved/paid); null = da liquidare. */
    private String liquidazioneStato;
    /** Es. "Set 2026" */
    private String liquidazionePeriodo;
    private Integer liquidazioneId;

    // Certificazione Unica
    /** cu_record.stato (draft/generated/delivered/sent); null = non generata. */
    private String cuStato;
    private Integer cuAnno;
    private Integer cuId;
}

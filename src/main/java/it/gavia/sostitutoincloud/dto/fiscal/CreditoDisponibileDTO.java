package it.gavia.sostitutoincloud.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Credito d'imposta da NDC ancora compensabile in un F24 (riga withholding_ledger 'credito_imposta'). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditoDisponibileDTO {

    private Integer ledgerId;
    private String ndcDocumentNumber;
    private LocalDate ndcDataEmissione;
    /** Residuo disponibile: ABS(ritenuta_amount) della riga di credito. */
    private BigDecimal importoCredito;
    /** Anno di emissione della NDC. */
    private Integer annoRiferimento;
}

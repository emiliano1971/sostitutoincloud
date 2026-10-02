package it.gavia.sostitutoincloud.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di emissione nota di credito (POST /api/ndc). La NDC è sempre totale:
 * le righe le ricava il backend dalle voci della fattura, il client passa solo la fattura.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmettNdcDTO {

    /** Fattura PM da stornare. */
    private Integer fkFiscalDocumentId;
}

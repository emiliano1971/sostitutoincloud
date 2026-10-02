package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Riga di una nota di credito (tabella fiscal_document_riga_ndc, migration 025).
 * Gli importi sono POSITIVI: il segno negativo è sul fiscal_document della NDC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiscalDocumentRigaNdc {

    private Integer id;
    private Integer fkFiscalDocumentId;
    private Integer fkTenantId;
    /** Riga di booking_split_economico stornata; null per riga libera. */
    private Integer fkSplitEconomicoId;
    private String descrizione;
    /** Lordo stornato (IVA inclusa). */
    private BigDecimal importoStornato;
    private BigDecimal imponibileStornato;
    private BigDecimal aliquotaIva;
    private Integer ordinamento;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;
}

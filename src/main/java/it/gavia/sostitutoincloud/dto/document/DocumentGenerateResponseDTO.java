package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerateResponseDTO {

    private Integer documentId;
    /** es. "RIC-2026-0003" */
    private String documentNumber;
    /** "ricevuta_owner" | "fattura_pm" */
    private String tipoDocumento;
    private LocalDate dataEmissione;
    private BigDecimal importoTotale;
    /** 2.00 se importo > 77.47, altrimenti 0 */
    private BigDecimal importoBollo;
    private BigDecimal imponibile;
    /** solo per fattura_pm */
    private BigDecimal iva;
    private BigDecimal ritenuta;
    /** "draft" */
    private String statoDocumento;
    private String bookingExternalId;
    private String guestName;
    private String ownerName;
    private String propertyName;
}

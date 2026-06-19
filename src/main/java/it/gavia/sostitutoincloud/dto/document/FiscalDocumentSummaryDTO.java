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
public class FiscalDocumentSummaryDTO {

    private Integer id;
    private String documentNumber;
    private String tipoDocumento;
    private String statoDocumento;
    private LocalDate dataEmissione;
    private BigDecimal importoTotale;
    private BigDecimal imponibile;
    private BigDecimal ritenutaAmount;
    private BigDecimal bolloAmount;
    private BigDecimal ivaAmount;
}

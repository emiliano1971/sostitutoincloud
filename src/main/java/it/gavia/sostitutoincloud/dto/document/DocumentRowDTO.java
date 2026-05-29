package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRowDTO {

    private String descrizione;
    private BigDecimal importoNetto;
    private BigDecimal aliquotaIva;
    private BigDecimal importoIva;
    private BigDecimal importoLordo;
}

package it.gavia.sostitutoincloud.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanaleOtaUpdateDTO {

    private String nome;
    private BigDecimal commissioneDefaultPct;
    private Boolean touristTaxIncluded;
    /** true = commissione nel file lorda (IVA inclusa); false = netta (es. Airbnb). Omesso = invariato. */
    private Boolean commissioneIvata;
    private String touristTaxCollection;
    private Boolean attivo;
}

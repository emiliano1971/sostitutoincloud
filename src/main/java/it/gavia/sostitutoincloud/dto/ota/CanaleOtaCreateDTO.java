package it.gavia.sostitutoincloud.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanaleOtaCreateDTO {

    private String codice;
    private String nome;
    private BigDecimal commissioneDefaultPct;
    private Boolean touristTaxIncluded;
    private String touristTaxCollection;
}

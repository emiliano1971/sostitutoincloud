package it.gavia.sostitutoincloud.dto.property;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyContractRuleCreateDTO {

    private Integer fkPropertyId;
    private Integer fkCanaleOtaId;
    private String tipo;
    private String calcMode;
    private BigDecimal valore;
    private Boolean isRemainder = false;
    private Integer ordine = 0;
}

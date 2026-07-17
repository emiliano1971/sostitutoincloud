package it.gavia.sostitutoincloud.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyContractRuleDTO {

    private Integer id;
    private Integer fkPropertyId;
    private Integer fkCanaleOtaId;
    private String canaleName;
    private String tipo;
    private String tipoLabel;
    private String calcMode;
    private String calcModeLabel;
    private BigDecimal valore;
    private Boolean isRemainder;
    private Integer ordine;
}

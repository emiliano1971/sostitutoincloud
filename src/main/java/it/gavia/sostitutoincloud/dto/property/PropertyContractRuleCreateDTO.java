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
    // Solo provvigione_proprietario. null in modifica = valore invariato; in creazione = false
    private Boolean addebitaBollo;
    private Integer ordine = 0;
}

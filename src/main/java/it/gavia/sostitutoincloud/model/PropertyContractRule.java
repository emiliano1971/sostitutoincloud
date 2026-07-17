package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyContractRule {

    private Integer id;
    private Integer fkPropertyId;
    private Integer fkTenantId;
    private Integer fkCanaleOtaId;
    private String tipo;
    private String calcMode;
    private BigDecimal valore;
    private Boolean isRemainder;
    private Integer ordine;
    private Boolean attivo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package it.gavia.sostitutoincloud.dto.ota;

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
public class CanaleOtaDTO {

    private Integer id;
    private String codice;
    private String nome;
    private BigDecimal commissioneDefaultPct;
    private Boolean touristTaxIncluded;
    private String touristTaxCollection;
    private Boolean attivo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

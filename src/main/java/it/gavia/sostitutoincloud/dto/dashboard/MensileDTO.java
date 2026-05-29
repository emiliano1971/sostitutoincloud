package it.gavia.sostitutoincloud.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensileDTO {

    private String mese;
    private String meseKey;
    private BigDecimal ricaviPm;
    private BigDecimal ricaviOw;
    private BigDecimal commissioni;
    private BigDecimal ritenute;
}

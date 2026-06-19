package it.gavia.sostitutoincloud.dto.touristtax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TassaFasciaEtaDTO {

    private String label;
    private Integer minAge;
    private Integer maxAge;
    private Integer reductionPct;
}

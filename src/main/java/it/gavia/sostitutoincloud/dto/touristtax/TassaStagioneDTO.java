package it.gavia.sostitutoincloud.dto.touristtax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TassaStagioneDTO {

    private String label;
    private Integer startMonth;
    private Integer startDay;
    private Integer endMonth;
    private Integer endDay;
    private Integer reductionPct;
}

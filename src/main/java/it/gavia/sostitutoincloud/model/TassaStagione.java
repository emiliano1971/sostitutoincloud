package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TassaStagione {

    private Integer id;
    private Integer fkRegolaId;
    private String label;
    private Integer startMonth;
    private Integer startDay;
    private Integer endMonth;
    private Integer endDay;
    private Integer reductionPct;
    private LocalDateTime createdAt;
}

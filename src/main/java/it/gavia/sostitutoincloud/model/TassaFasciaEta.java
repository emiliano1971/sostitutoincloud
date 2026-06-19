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
public class TassaFasciaEta {

    private Integer id;
    private Integer fkRegolaId;
    private String label;
    private Integer minAge;
    private Integer maxAge;
    private Integer reductionPct;
    private LocalDateTime createdAt;
}

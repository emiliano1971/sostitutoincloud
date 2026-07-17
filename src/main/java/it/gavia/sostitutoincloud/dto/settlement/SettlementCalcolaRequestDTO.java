package it.gavia.sostitutoincloud.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCalcolaRequestDTO {

    private Integer mese; // 1-12
    private Integer anno; // es. 2026
}

package it.gavia.sostitutoincloud.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCalcolaResultDTO {

    private Integer generated; // nuovi settlement creati
    private Integer updated;   // settlement ricalcolati
    private Integer skipped;   // saltati (già paid)
    private List<SettlementListDTO> settlements;
}

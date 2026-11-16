package it.gavia.sostitutoincloud.dto.cu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Risposta della generazione singola quando la CU draft/generated non ha più ritenute nel
 * registro: invece di rigenerarla (non ci sarebbe nulla da aggregare) viene eliminata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuEliminataDTO {
    private boolean eliminata;
    private Integer cuId;
    private String ownerName;
    private Integer taxYear;
    private String message;
}

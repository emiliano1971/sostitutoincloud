package it.gavia.sostitutoincloud.dto.owner;

import it.gavia.sostitutoincloud.dto.dashboard.MensileDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDashboardDTO {

    private BigDecimal ricaviTotali;
    private Integer prenotazioniCount;
    private BigDecimal totalRitenute;
    private BigDecimal totalLiquidato;
    private List<MensileDTO> ricaviMensili;

    // Portale owner: canone maturato, numero liquidazioni e quanto resta da incassare.
    // ricaviTotali è il lordo ospite, totalNet il canone che spetta al proprietario.
    private BigDecimal totalNet;
    private Integer settlementsCount;
    /** Somma dei netti delle liquidazioni non ancora pagate (stato != 'paid'). */
    private BigDecimal netDaPagare;
}
